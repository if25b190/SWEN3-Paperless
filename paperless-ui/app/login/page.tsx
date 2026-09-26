"use client";

import React, { useState, useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/context";
import { useTranslation } from "@/lib/i18n/context";
import { FileText, Loader2, Lock, User, AlertCircle } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Navbar } from "@/components/Navbar";
import { ApiError } from "@/lib/api/client";

function LoginContent() {
  const { user, login, register, loading: authLoading } = useAuth();
  const { t } = useTranslation();
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialTab = searchParams.get("tab") === "register" ? "register" : "login";
  const [activeTab, setActiveTab] = useState<"login" | "register">(initialTab);

  // Login form state
  const [loginUsername, setLoginUsername] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [loginSubmitting, setLoginSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  // Register form state
  const [regUsername, setRegUsername] = useState("");
  const [regPassword, setRegPassword] = useState("");
  const [regConfirmPassword, setRegConfirmPassword] = useState("");
  const [regSubmitting, setRegSubmitting] = useState(false);
  const [regError, setRegError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && user) {
      router.replace("/dashboard");
    }
  }, [user, authLoading, router]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError(null);

    if (!loginUsername.trim() || !loginPassword) {
      setLoginError("Please enter both username and password.");
      return;
    }

    setLoginSubmitting(true);
    try {
      await login({ username: loginUsername.trim(), password: loginPassword });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setLoginError(err.message);
      } else {
        setLoginError("Failed to sign in. Please verify your credentials.");
      }
    } finally {
      setLoginSubmitting(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setRegError(null);

    if (regUsername.trim().length < 4) {
      setRegError(t.auth.usernameTooShort);
      return;
    }
    if (regPassword.length < 8) {
      setRegError(t.auth.passwordTooShort);
      return;
    }
    if (regPassword !== regConfirmPassword) {
      setRegError(t.auth.passwordsDoNotMatch);
      return;
    }

    setRegSubmitting(true);
    try {
      await register({ username: regUsername.trim(), password: regPassword });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setRegError("Username is already taken. Please choose another username.");
        } else if (err.invalidParams.length > 0) {
          setRegError(err.invalidParams.map((p) => p.reason).join(" "));
        } else {
          setRegError(err.message);
        }
      } else {
        setRegError("Registration failed. Please try again.");
      }
    } finally {
      setRegSubmitting(false);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Navbar />
      <div className="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8">
        <div className="w-full max-w-md space-y-6">
          <div className="text-center space-y-2">
            <div className="inline-flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600 text-white shadow-md">
              <FileText className="h-6 w-6" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              {t.common.appName}
            </h1>
            <p className="text-sm text-slate-500">{t.common.tagline}</p>
          </div>

          <Card className="border-slate-200 shadow-sm bg-white">
            <CardHeader className="pb-4">
              <Tabs
                value={activeTab}
                onValueChange={(val) => {
                  setActiveTab(val as "login" | "register");
                  setLoginError(null);
                  setRegError(null);
                }}
                className="w-full"
              >
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="login">{t.nav.login}</TabsTrigger>
                  <TabsTrigger value="register">{t.nav.register}</TabsTrigger>
                </TabsList>

                {/* LOGIN FORM */}
                <TabsContent value="login" className="pt-4">
                  <div className="space-y-1 mb-4 text-center">
                    <CardTitle className="text-lg font-semibold">
                      {t.auth.loginTitle}
                    </CardTitle>
                    <CardDescription>{t.auth.loginSubtitle}</CardDescription>
                  </div>

                  {loginError && (
                    <div className="mb-4 flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                      <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-600" />
                      <span>{loginError}</span>
                    </div>
                  )}

                  <form onSubmit={handleLogin} className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="login-username">{t.auth.username}</Label>
                      <div className="relative">
                        <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                          id="login-username"
                          type="text"
                          placeholder={t.auth.usernamePlaceholder}
                          value={loginUsername}
                          onChange={(e) => setLoginUsername(e.target.value)}
                          className="pl-9"
                          required
                          autoComplete="username"
                        />
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <Label htmlFor="login-password">{t.auth.password}</Label>
                      <div className="relative">
                        <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                          id="login-password"
                          type="password"
                          placeholder={t.auth.passwordPlaceholder}
                          value={loginPassword}
                          onChange={(e) => setLoginPassword(e.target.value)}
                          className="pl-9"
                          required
                          autoComplete="current-password"
                        />
                      </div>
                    </div>

                    <Button
                      type="submit"
                      className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium"
                      disabled={loginSubmitting}
                    >
                      {loginSubmitting ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          {t.common.loading}
                        </>
                      ) : (
                        t.auth.loginButton
                      )}
                    </Button>
                  </form>
                </TabsContent>

                {/* REGISTER FORM */}
                <TabsContent value="register" className="pt-4">
                  <div className="space-y-1 mb-4 text-center">
                    <CardTitle className="text-lg font-semibold">
                      {t.auth.registerTitle}
                    </CardTitle>
                    <CardDescription>{t.auth.registerSubtitle}</CardDescription>
                  </div>

                  {regError && (
                    <div className="mb-4 flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                      <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-600" />
                      <span>{regError}</span>
                    </div>
                  )}

                  <form onSubmit={handleRegister} className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="reg-username">{t.auth.username}</Label>
                      <div className="relative">
                        <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                          id="reg-username"
                          type="text"
                          placeholder={t.auth.usernamePlaceholder}
                          value={regUsername}
                          onChange={(e) => setRegUsername(e.target.value)}
                          className="pl-9"
                          required
                          minLength={4}
                          maxLength={50}
                          autoComplete="username"
                        />
                      </div>
                      <p className="text-xs text-slate-500">
                        {t.auth.usernameTooShort}
                      </p>
                    </div>

                    <div className="space-y-1.5">
                      <Label htmlFor="reg-password">{t.auth.password}</Label>
                      <div className="relative">
                        <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                          id="reg-password"
                          type="password"
                          placeholder={t.auth.passwordPlaceholder}
                          value={regPassword}
                          onChange={(e) => setRegPassword(e.target.value)}
                          className="pl-9"
                          required
                          minLength={8}
                          maxLength={100}
                          autoComplete="new-password"
                        />
                      </div>
                      <p className="text-xs text-slate-500">
                        {t.auth.passwordTooShort}
                      </p>
                    </div>

                    <div className="space-y-1.5">
                      <Label htmlFor="reg-confirm-password">
                        {t.auth.confirmPassword}
                      </Label>
                      <div className="relative">
                        <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                          id="reg-confirm-password"
                          type="password"
                          placeholder={t.auth.confirmPasswordPlaceholder}
                          value={regConfirmPassword}
                          onChange={(e) => setRegConfirmPassword(e.target.value)}
                          className="pl-9"
                          required
                          autoComplete="new-password"
                        />
                      </div>
                    </div>

                    <Button
                      type="submit"
                      className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium"
                      disabled={regSubmitting}
                    >
                      {regSubmitting ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          {t.common.loading}
                        </>
                      ) : (
                        t.auth.registerButton
                      )}
                    </Button>
                  </form>
                </TabsContent>
              </Tabs>
            </CardHeader>
          </Card>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center bg-slate-50">
          <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
        </div>
      }
    >
      <LoginContent />
    </Suspense>
  );
}

