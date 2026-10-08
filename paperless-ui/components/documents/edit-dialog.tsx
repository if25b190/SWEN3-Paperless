"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/services";
import { NONE_VALUE, PRIVATE_VALUE } from "@/lib/documents";
import { useDocumentTypes, useMemberships } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n/use-i18n";
import type { DocumentItem, UpdateDocumentPayload } from "@/lib/types";

function EditForm({
  document,
  onCancel,
  onSaved,
}: {
  document: DocumentItem;
  onCancel: () => void;
  onSaved: (document: DocumentItem) => void;
}) {
  const { t } = useI18n();
  const { user } = useAuth();
  const { types } = useDocumentTypes();
  const { writableTeams, teamById } = useMemberships();
  const [title, setTitle] = useState(document.title);
  const [typeId, setTypeId] = useState(document.document_type?.id ?? NONE_VALUE);
  const initialTeam = document.team_id ?? PRIVATE_VALUE;
  const [teamId, setTeamId] = useState(initialTeam);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const isUploader = user?.id === document.owner_id;

  // Keep the current team selectable even when it is not in the writable list.
  const teamOptions = [...writableTeams];
  if (document.team_id && !teamOptions.some((team) => team.id === document.team_id)) {
    const current = teamById.get(document.team_id);
    teamOptions.push(
      current ?? {
        id: document.team_id,
        name: t("documents.team_unknown"),
        owner_id: "",
        created_at: "",
      },
    );
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = t("validation.required");
    else if (title.trim().length > 255) next.title = t("validation.max_length", { max: 255 });
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const payload: UpdateDocumentPayload = {
      title: title.trim(),
      document_type_id: typeId === NONE_VALUE ? null : typeId,
    };
    if (isUploader && teamId !== initialTeam) {
      if (teamId === PRIVATE_VALUE) payload.clear_team = true;
      else payload.team_id = teamId;
    }

    setSubmitting(true);
    try {
      const updated = await documentsApi.update(document.id, payload);
      toast.success(t("messages.document_updated"));
      onSaved(updated);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <DialogHeader>
        <DialogTitle>{t("documents.edit_title")}</DialogTitle>
        <DialogDescription>{document.original_filename}</DialogDescription>
      </DialogHeader>

      <Field label={t("documents.field_title")} htmlFor="edit-title" error={errors.title}>
        <Input
          id="edit-title"
          value={title}
          maxLength={255}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={Boolean(errors.title)}
        />
      </Field>

      <Field label={t("documents.field_type")} optional error={errors.document_type_id}>
        <Select value={typeId} onValueChange={setTypeId}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE_VALUE}>{t("documents.no_type")}</SelectItem>
            {types.map((type) => (
              <SelectItem key={type.id} value={type.id}>
                {type.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      <Field
        label={t("documents.field_team")}
        hint={isUploader ? t("documents.sharing_hint") : t("documents.sharing_owner_only")}
        error={errors.team_id ?? errors.clear_team}
      >
        <Select value={teamId} onValueChange={setTeamId} disabled={!isUploader}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={PRIVATE_VALUE}>{t("documents.team_private")}</SelectItem>
            {teamOptions.map((team) => (
              <SelectItem key={team.id} value={team.id}>
                {team.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {t("common.save")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function EditDocumentDialog({
  document,
  onOpenChange,
  onSaved,
}: {
  document: DocumentItem | null;
  onOpenChange: (open: boolean) => void;
  onSaved: (document: DocumentItem) => void;
}) {
  const { t } = useI18n();
  return (
    <Dialog open={document !== null} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("common.close")}>
        {document && (
          <EditForm
            key={document.id}
            document={document}
            onCancel={() => onOpenChange(false)}
            onSaved={(saved) => {
              onOpenChange(false);
              onSaved(saved);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
