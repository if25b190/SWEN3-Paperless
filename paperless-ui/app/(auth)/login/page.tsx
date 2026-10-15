"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!username.trim()) next.username = "This field is required.";
    if (!password) next.password = "This field is required.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      const user = await login(username.trim(), password);
      toast.success(`Welcome back, ${user.username}.`);
      router.replace("/");
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <div>
        <h2 className="text-lg font-semibold">Welcome back</h2>
        <p className="text-sm text-muted-foreground">Sign in to manage your documents.</p>
      </div>
      <Field label="Username" htmlFor="username" error={errors.username}>
        <Input
          id="username"
          autoComplete="username"
          autoFocus
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          aria-invalid={Boolean(errors.username)}
        />
      </Field>
      <Field label="Password" htmlFor="password" error={errors.password}>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={Boolean(errors.password)}
        />
      </Field>
      <Button type="submit" disabled={submitting}>
        {submitting && <Spinner />}
        Sign in
      </Button>
    </form>
  );
}
