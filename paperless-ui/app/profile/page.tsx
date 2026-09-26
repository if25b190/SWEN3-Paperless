"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import { useAuth } from "@/lib/auth/context";
import { usersApi, ApiError } from "@/lib/api/client";
import { UserTeamMembershipResponse, Role } from "@/lib/types/api";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import {
  User,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  Loader2,
  Users,
  AlertCircle,
  KeyRound,
  ArrowRight,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";

export default function ProfilePage() {
  const { t } = useTranslation();
  const { user, refreshUser, logout } = useAuth();

  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [updating, setUpdating] = useState(false);
  const [updateError, setUpdateError] = useState<string | null>(null);

  const [teams, setTeams] = useState<UserTeamMembershipResponse[]>([]);
  const [teamsLoading, setTeamsLoading] = useState(true);

  const [deleteAccountOpen, setDeleteAccountOpen] = useState(false);
  const [deletingAccount, setDeletingAccount] = useState(false);

  const fetchUserTeams = useCallback(async () => {
    if (!user?.id) return;
    setTeamsLoading(true);
    try {
      const res = await usersApi.getUserTeams(user.id);
      setTeams(res.items || []);
    } catch {
      // Handled by api client
    } finally {
      setTeamsLoading(false);
    }
  }, [user]);

  useEffect(() => {
    fetchUserTeams();
  }, [fetchUserTeams]);

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    setUpdateError(null);

    const hasNewUsername = newUsername.trim().length > 0;
    const hasNewPassword = newPassword.length > 0;

    if (!hasNewUsername && !hasNewPassword) {
      toast.info("No changes provided.");
      return;
    }

    if (hasNewUsername && newUsername.trim().length < 4) {
      setUpdateError(t.auth.usernameTooShort);
      return;
    }

    if (hasNewPassword) {
      if (newPassword.length < 8) {
        setUpdateError(t.auth.passwordTooShort);
        return;
      }
      if (newPassword !== confirmPassword) {
        setUpdateError(t.auth.passwordsDoNotMatch);
        return;
      }
    }

    setUpdating(true);
    try {
      await usersApi.updateUser(user.id, {
        username: hasNewUsername ? newUsername.trim() : undefined,
        password: hasNewPassword ? newPassword : undefined,
      });

      toast.success(t.profile.updateSuccess);
      setNewUsername("");
      setNewPassword("");
      setConfirmPassword("");
      await refreshUser();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setUpdateError("Username already in use by another account.");
        } else {
          setUpdateError(err.message);
        }
      } else {
        setUpdateError("Failed to update profile.");
      }
    } finally {
      setUpdating(false);
    }
  };

  const handleDeleteAccount = async () => {
    if (!user) return;
    setDeletingAccount(true);
    try {
      await usersApi.deleteUser(user.id);
      toast.success(t.profile.deleteAccountSuccess);
      logout();
    } catch {
      // Handled by client
    } finally {
      setDeletingAccount(false);
    }
  };

  const getRoleBadge = (role: Role) => {
    switch (role) {
      case "ADMIN":
        return (
          <Badge variant="purple" className="gap-1 font-semibold text-xs">
            <ShieldAlert className="h-3 w-3" />
            {t.roles.ADMIN}
          </Badge>
        );
      case "READ_WRITE":
        return (
          <Badge variant="info" className="gap-1 font-medium text-xs">
            <ShieldCheck className="h-3 w-3" />
            {t.roles.READ_WRITE}
          </Badge>
        );
      case "READONLY":
        return (
          <Badge variant="secondary" className="gap-1 font-medium text-xs">
            <Shield className="h-3 w-3 text-slate-500" />
            {t.roles.READONLY}
          </Badge>
        );
    }
  };

  const formatDate = (isoString?: string) => {
    if (!isoString) return "-";
    try {
      return new Date(isoString).toLocaleDateString(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
      });
    } catch {
      return isoString;
    }
  };

  return (
    <ProtectedRoute>
      <div className="space-y-6 max-w-4xl">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            {t.profile.title}
          </h1>
          <p className="text-sm text-slate-500">{t.profile.subtitle}</p>
        </div>

        {/* User Info Overview */}
        <Card className="border-slate-200 bg-white shadow-xs">
          <CardHeader className="pb-3 border-b border-slate-100">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <User className="h-4 w-4 text-blue-600" />
              {t.profile.userInfo}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4 grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
            <div>
              <p className="text-xs text-slate-500">{t.profile.username}</p>
              <p className="font-semibold text-slate-900 text-base mt-0.5">
                {user?.username}
              </p>
            </div>
            <div>
              <p className="text-xs text-slate-500">{t.profile.userId}</p>
              <p className="font-mono text-xs text-slate-700 mt-1 truncate">
                {user?.id}
              </p>
            </div>
            <div>
              <p className="text-xs text-slate-500">{t.profile.memberSince}</p>
              <p className="text-slate-700 mt-0.5">
                {formatDate(user?.created_at)}
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Update Profile Form */}
        <Card className="border-slate-200 bg-white shadow-xs">
          <CardHeader className="pb-3 border-b border-slate-100">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-blue-600" />
              {t.profile.updateAccount}
            </CardTitle>
            <CardDescription>
              Change your username or password. Leave fields empty if you do not wish to update them.
            </CardDescription>
          </CardHeader>
          <CardContent className="p-6">
            {updateError && (
              <div className="mb-4 flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-600" />
                <span>{updateError}</span>
              </div>
            )}

            <form onSubmit={handleUpdateProfile} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="profile-username">{t.profile.newUsername}</Label>
                <Input
                  id="profile-username"
                  placeholder={user?.username}
                  value={newUsername}
                  onChange={(e) => setNewUsername(e.target.value)}
                  minLength={4}
                  maxLength={50}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="profile-password">{t.profile.newPassword}</Label>
                  <Input
                    id="profile-password"
                    type="password"
                    placeholder={t.profile.newPasswordPlaceholder}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    minLength={8}
                    maxLength={100}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="profile-confirm-password">
                    {t.auth.confirmPassword}
                  </Label>
                  <Input
                    id="profile-confirm-password"
                    type="password"
                    placeholder={t.auth.confirmPasswordPlaceholder}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                  />
                </div>
              </div>

              <div className="pt-2">
                <Button
                  type="submit"
                  disabled={updating || (!newUsername.trim() && !newPassword)}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {updating ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.common.save
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        {/* My Team Memberships */}
        <Card className="border-slate-200 bg-white shadow-xs">
          <CardHeader className="pb-3 border-b border-slate-100">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <Users className="h-4 w-4 text-blue-600" />
              {t.profile.myTeams}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4">
            {teamsLoading ? (
              <div className="flex items-center justify-center p-6 text-slate-500">
                <Loader2 className="h-6 w-6 animate-spin text-blue-600 mr-2" />
                <span className="text-sm">{t.common.loading}</span>
              </div>
            ) : teams.length === 0 ? (
              <p className="text-sm text-slate-500 py-3">{t.profile.noTeams}</p>
            ) : (
              <div className="divide-y divide-slate-100">
                {teams.map(({ team, role }) => (
                  <div
                    key={team.id}
                    className="py-3 flex items-center justify-between"
                  >
                    <div>
                      <Link
                        href={`/teams/${team.id}`}
                        className="font-medium text-slate-900 hover:text-blue-600 text-sm hover:underline"
                      >
                        {team.name}
                      </Link>
                      {team.description && (
                        <p className="text-xs text-slate-400 mt-0.5 line-clamp-1">
                          {team.description}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      {getRoleBadge(role)}
                      <Link
                        href={`/teams/${team.id}`}
                        className="text-slate-400 hover:text-slate-700"
                      >
                        <ArrowRight className="h-4 w-4" />
                      </Link>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Danger Zone */}
        <Card className="border-red-200 bg-white shadow-xs">
          <CardHeader className="pb-3 border-b border-red-100">
            <CardTitle className="text-base font-semibold text-red-600 flex items-center gap-2">
              <Trash2 className="h-4 w-4" />
              {t.profile.dangerZone}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-slate-900">
                {t.profile.deleteAccount}
              </p>
              <p className="text-xs text-slate-500 mt-0.5">
                {t.profile.deleteAccountWarning}
              </p>
            </div>
            <Button
              variant="destructive"
              onClick={() => setDeleteAccountOpen(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-medium"
            >
              {t.profile.deleteAccount}
            </Button>
          </CardContent>
        </Card>

        {/* Delete Account Confirmation Dialog */}
        <ConfirmDialog
          open={deleteAccountOpen}
          onOpenChange={setDeleteAccountOpen}
          title={t.profile.deleteAccountConfirmTitle}
          description={t.profile.deleteAccountWarning}
          confirmLabel={t.profile.deleteAccount}
          loading={deletingAccount}
          onConfirm={handleDeleteAccount}
        />
      </div>
    </ProtectedRoute>
  );
}
