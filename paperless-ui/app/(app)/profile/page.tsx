"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Trash2 } from "lucide-react";
import { toast } from "sonner";
import { ConfirmDialog, Field, PageHeader, Spinner } from "@/components/common";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { usersApi } from "@/lib/api/services";
import { formatDay } from "@/lib/utils";
import type { User } from "@/lib/types";

function ProfileForm({ user }: { user: User }) {
  const { setUser } = useAuth();
  const [username, setUsername] = useState(user.username);
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    const name = username.trim();
    if (name.length < 4) next.username = "Username must be at least 4 characters.";
    else if (name.length > 50) next.username = "Username must be at most 50 characters.";
    if (password && password.length < 8) next.password = "Password must be at least 8 characters.";
    else if (password.length > 100) next.password = "Password must be at most 100 characters.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      const updated = await usersApi.update(
        user.id,
        {
          username: name !== user.username ? name : undefined,
          password: password || undefined,
        },
        { messages: { 409: "Username already exists." } },
      );
      setUser(updated);
      setPassword("");
      toast.success("Profile updated.");
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
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
        <CardDescription>
          Member since {formatDay(user.created_at)}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid max-w-md gap-4" noValidate>
          <Field label="Username" htmlFor="profile-username" error={errors.username}>
            <Input
              id="profile-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              aria-invalid={Boolean(errors.username)}
            />
          </Field>
          <Field
            label="New password"
            htmlFor="profile-password"
            hint="Leave empty to keep your current password."
            error={errors.password}
          >
            <Input
              id="profile-password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(errors.password)}
            />
          </Field>
          <div>
            <Button type="submit" disabled={submitting}>
              {submitting && <Spinner />}
              Save
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  if (!user) return null;

  async function onDelete() {
    if (!user) return;
    setBusy(true);
    try {
      await usersApi.remove(user.id);
      toast.success("Your account was deleted.");
      logout();
      router.replace("/login");
    } catch {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-6">
      <PageHeader title="Profile" description="Manage your own account." />
      <ProfileForm key={user.id} user={user} />
      <Card className="border-destructive/30">
        <CardHeader>
          <CardTitle className="text-destructive">Delete account</CardTitle>
          <CardDescription>This permanently deletes your account. This cannot be undone.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={() => setConfirmOpen(true)}>
            <Trash2 />
            Delete account
          </Button>
        </CardContent>
      </Card>
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Delete your account?"
        description="You will be signed out and lose access to your documents."
        confirmLabel="Delete"
        loading={busy}
        onConfirm={onDelete}
      />
    </div>
  );
}
