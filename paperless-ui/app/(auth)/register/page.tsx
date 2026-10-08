"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { usersApi } from "@/lib/api/services";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function RegisterPage() {
  const { t } = useI18n();
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  function validate() {
    const next: Record<string, string> = {};
    const name = username.trim();
    if (!name) next.username = t("validation.required");
    else if (name.length < 4) next.username = t("validation.username_min");
    else if (name.length > 50) next.username = t("validation.username_max");
    if (!password) next.password = t("validation.required");
    else if (password.length < 8) next.password = t("validation.password_min");
    else if (password.length > 100) next.password = t("validation.password_max");
    if (confirm !== password) next.confirm = t("validation.password_mismatch");
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
        { anonymous: true, messages: { 409: "errors.username_exists" } },
      );
      toast.success(t("messages.registered"));
      router.replace("/login");
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
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <div>
        <h2 className="text-lg font-semibold">{t("auth.register_title")}</h2>
        <p className="text-sm text-muted-foreground">{t("auth.register_subtitle")}</p>
      </div>
      <Field label={t("auth.username")} htmlFor="username" error={errors.username}>
        <Input
          id="username"
          autoComplete="username"
          autoFocus
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          aria-invalid={Boolean(errors.username)}
        />
      </Field>
      <Field label={t("auth.password")} htmlFor="password" error={errors.password}>
        <Input
          id="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={Boolean(errors.password)}
        />
      </Field>
      <Field label={t("auth.confirm_password")} htmlFor="confirm" error={errors.confirm}>
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
        {t("auth.create_account")}
      </Button>
    </form>
  );
}
