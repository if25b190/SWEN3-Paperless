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

  const [name, setName] = useState(team?.name ?? "");
  const [description, setDescription] = useState(team?.description ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!name.trim()) next.name = "This field is required.";
    else if (name.trim().length > 100) next.name = `Must be at most 100 characters.`;
    if (description.length > 255) next.description = `Must be at most 255 characters.`;
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const body = { name: name.trim(), description: description.trim() || (team ? "" : null) };
    setSubmitting(true);
    try {
      const saved = team ? await teamsApi.update(team.id, body) : await teamsApi.create(body);
      toast.success((team ? "Team updated." : "Team created."));
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
        <DialogTitle>{(team ? "Edit team" : "Create team")}</DialogTitle>
      </DialogHeader>
      <Field label="Team name" htmlFor="team-name" error={errors.name}>
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
        label="Description"
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
          Cancel
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {(team ? "Save" : "Create")}
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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel="Close">
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
