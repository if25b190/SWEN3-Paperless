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
import { useI18n } from "@/lib/i18n/use-i18n";
import { formatDay } from "@/lib/utils";
import type { User } from "@/lib/types";

function ProfileForm({ user }: { user: User }) {
  const { t, locale } = useI18n();
  const { setUser } = useAuth();
  const [username, setUsername] = useState(user.username);
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    const name = username.trim();
    if (name.length < 4) next.username = t("validation.username_min");
    else if (name.length > 50) next.username = t("validation.username_max");
    if (password && password.length < 8) next.password = t("validation.password_min");
    else if (password.length > 100) next.password = t("validation.password_max");
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
        { messages: { 409: "errors.username_exists" } },
      );
      setUser(updated);
      setPassword("");
      toast.success(t("messages.profile_updated"));
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(
          error.status === 409 ? { username: t("errors.username_exists") } : error.fieldErrors,
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("nav.profile")}</CardTitle>
        <CardDescription>
          {t("profile.member_since")} {formatDay(user.created_at, locale)}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="grid max-w-md gap-4" noValidate>
          <Field label={t("auth.username")} htmlFor="profile-username" error={errors.username}>
            <Input
              id="profile-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              aria-invalid={Boolean(errors.username)}
            />
          </Field>
          <Field
            label={t("profile.new_password")}
            htmlFor="profile-password"
            hint={t("profile.new_password_hint")}
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
              {t("common.save")}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

export default function ProfilePage() {
  const { t } = useI18n();
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
      toast.success(t("messages.account_deleted"));
      logout();
      router.replace("/login");
    } catch {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-6">
      <PageHeader title={t("profile.title")} description={t("profile.subtitle")} />
      <ProfileForm key={user.id} user={user} />
      <Card className="border-destructive/30">
        <CardHeader>
          <CardTitle className="text-destructive">{t("profile.danger_title")}</CardTitle>
          <CardDescription>{t("profile.danger_text")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={() => setConfirmOpen(true)}>
            <Trash2 />
            {t("profile.danger_title")}
          </Button>
        </CardContent>
      </Card>
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t("profile.delete_confirm_title")}
        description={t("profile.delete_confirm_text")}
        confirmLabel={t("common.delete")}
        loading={busy}
        onConfirm={onDelete}
      />
    </div>
  );
}
