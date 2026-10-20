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
        name: "Team",
        owner_id: "",
        created_at: "",
      },
    );
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = "This field is required.";
    else if (title.trim().length > 255) next.title = `Must be at most 255 characters.`;
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const payload: UpdateDocumentPayload = {
      title: title.trim(),
      document_type_id: typeId === NONE_VALUE ? undefined : typeId,
      clear_document_type: typeId === NONE_VALUE,
    };
    if (isUploader && teamId !== initialTeam) {
      if (teamId === PRIVATE_VALUE) payload.clear_team = true;
      else payload.team_id = teamId;
    }

    setSubmitting(true);
    try {
      const updated = await documentsApi.update(document.id, payload);
      toast.success("Document updated.");
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
        <DialogTitle>Edit document</DialogTitle>
        <DialogDescription>{document.original_filename}</DialogDescription>
      </DialogHeader>

      <Field label="Title" htmlFor="edit-title" error={errors.title}>
        <Input
          id="edit-title"
          value={title}
          maxLength={255}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={Boolean(errors.title)}
        />
      </Field>

      <Field label="Document type" optional error={errors.document_type_id}>
        <Select value={typeId} onValueChange={setTypeId}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE_VALUE}>No type</SelectItem>
            {types.map((type) => (
              <SelectItem key={type.id} value={type.id}>
                {type.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      <Field
        label="Team sharing"
        hint={isUploader ? "Only teams where you can write are listed." : "Only the uploader can change team sharing."}
        error={errors.team_id ?? errors.clear_team}
      >
        <Select value={teamId} onValueChange={setTeamId} disabled={!isUploader}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={PRIVATE_VALUE}>Private (only me)</SelectItem>
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
          Cancel
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          Save
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

  return (
    <Dialog open={document !== null} onOpenChange={onOpenChange}>
      <DialogContent closeLabel="Close">
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
