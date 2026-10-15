"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { usersApi } from "@/lib/api/services";

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const next: Record<string, string> = {};
    const name = username.trim();
    if (!name) next.username = "This field is required.";
    else if (name.length < 4) next.username = "Username must be at least 4 characters.";
    else if (name.length > 50) next.username = "Username must be at most 50 characters.";
    if (!password) next.password = "This field is required.";
    else if (password.length < 8) next.password = "Password must be at least 8 characters.";
    else if (password.length > 100) next.password = "Password must be at most 100 characters.";
    if (confirm !== password) next.confirm = "Passwords do not match.";
    return next;
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      await usersApi.register(
        { username: username.trim(), password },
        { anonymous: true, messages: { 409: "Username already exists." } },
      );
      toast.success("Account created. You can sign in now.");
      router.replace("/login");
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(
          error.status === 409 ? { username: "Username already exists." } : error.fieldErrors,
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <div>
        <h2 className="text-lg font-semibold">Create your account</h2>
        <p className="text-sm text-muted-foreground">Register to start organizing documents.</p>
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
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={Boolean(errors.password)}
        />
      </Field>
      <Field label="Confirm password" htmlFor="confirm" error={errors.confirm}>
        <Input
          id="confirm"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(event) => setConfirm(event.target.value)}
          aria-invalid={Boolean(errors.confirm)}
        />
      </Field>
      <Button type="submit" disabled={submitting}>
        {submitting && <Spinner />}
        Create account
      </Button>
    </form>
  );
}
