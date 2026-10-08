"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input, Textarea } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { teamsApi } from "@/lib/api/services";
import { useI18n } from "@/lib/i18n/use-i18n";
import type { Team } from "@/lib/types";

function TeamForm({
  team,
  onCancel,
  onSaved,
}: {
  team: Team | null;
  onCancel: () => void;
  onSaved: (team: Team) => void;
}) {
  const { t } = useI18n();
  const [name, setName] = useState(team?.name ?? "");
  const [description, setDescription] = useState(team?.description ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!name.trim()) next.name = t("validation.required");
    else if (name.trim().length > 100) next.name = t("validation.max_length", { max: 100 });
    if (description.length > 255) next.description = t("validation.max_length", { max: 255 });
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const body = { name: name.trim(), description: description.trim() || null };
    setSubmitting(true);
    try {
      const saved = team ? await teamsApi.update(team.id, body) : await teamsApi.create(body);
      toast.success(t(team ? "messages.team_updated" : "messages.team_created"));
      onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <DialogHeader>
        <DialogTitle>{t(team ? "teams.edit_title" : "teams.create_title")}</DialogTitle>
      </DialogHeader>
      <Field label={t("teams.field_name")} htmlFor="team-name" error={errors.name}>
        <Input
          id="team-name"
          value={name}
          maxLength={100}
          autoFocus
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(errors.name)}
        />
      </Field>
      <Field
        label={t("teams.field_description")}
        htmlFor="team-description"
        optional
        error={errors.description}
      >
        <Textarea
          id="team-description"
          value={description}
          maxLength={255}
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={Boolean(errors.description)}
        />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {t(team ? "common.save" : "common.create")}
        </Button>
      </DialogFooter>
    </form>
  );
}

/** Create (team === null) or edit a team. Pass `open` to control visibility. */
export function TeamDialog({
  open,
  team,
  onOpenChange,
  onSaved,
}: {
  open: boolean;
  team: Team | null;
  onOpenChange: (open: boolean) => void;
  onSaved: (team: Team) => void;
}) {
  const { t } = useI18n();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("common.close")}>
        <TeamForm
          team={team}
          onCancel={() => onOpenChange(false)}
          onSaved={(saved) => {
            onOpenChange(false);
            onSaved(saved);
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
