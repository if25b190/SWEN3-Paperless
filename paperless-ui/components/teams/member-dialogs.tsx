"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
import { UserCombobox } from "@/components/teams/user-combobox";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError } from "@/lib/api/client";
import { teamsApi } from "@/lib/api/services";
import { useI18n } from "@/lib/i18n/use-i18n";
import { ROLES, type Role, type TeamMember, type User } from "@/lib/types";

function RoleSelect({ value, onChange }: { value: Role; onChange: (role: Role) => void }) {
  const { t } = useI18n();
  return (
    <Select value={value} onValueChange={(next) => onChange(next as Role)}>
      <SelectTrigger>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {ROLES.map((role) => (
          <SelectItem key={role} value={role}>
            {t(`roles.${role}`)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function AddMemberForm({
  teamId,
  candidates,
  onCancel,
  onAdded,
}: {
  teamId: string;
  candidates: User[];
  onCancel: () => void;
  onAdded: () => void;
}) {
  const { t } = useI18n();
  const [userId, setUserId] = useState("");
  const [role, setRole] = useState<Role>("READONLY");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!userId) {
      setErrors({ user_id: t("validation.required") });
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await teamsApi.addMember(
        teamId,
        { user_id: userId, role },
        { messages: { 409: "errors.already_member" } },
      );
      toast.success(t("messages.member_added_success"));
      onAdded();
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <DialogHeader>
        <DialogTitle>{t("teams.add_member_title")}</DialogTitle>
        <DialogDescription>{t("teams.add_member_text")}</DialogDescription>
      </DialogHeader>
      <Field label={t("teams.pick_user")} error={errors.user_id}>
        <UserCombobox
          users={candidates}
          value={userId}
          onChange={setUserId}
          invalid={Boolean(errors.user_id)}
        />
      </Field>
      <Field label={t("teams.role")} hint={t(`role_descriptions.${role}`)} error={errors.role}>
        <RoleSelect value={role} onChange={setRole} />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {t("actions.add_member")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function AddMemberDialog({
  open,
  onOpenChange,
  teamId,
  candidates,
  onAdded,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  teamId: string;
  candidates: User[];
  onAdded: () => void;
}) {
  const { t } = useI18n();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("common.close")}>
        <AddMemberForm
          teamId={teamId}
          candidates={candidates}
          onCancel={() => onOpenChange(false)}
          onAdded={() => {
            onOpenChange(false);
            onAdded();
          }}
        />
      </DialogContent>
    </Dialog>
  );
}

function RoleForm({
  teamId,
  member,
  onCancel,
  onSaved,
}: {
  teamId: string;
  member: TeamMember;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const { t } = useI18n();
  const [role, setRole] = useState<Role>(member.role);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await teamsApi.updateMemberRole(teamId, member.user.id, role);
      toast.success(t("messages.role_updated_success"));
      onSaved();
    } catch {
      // toast already shown by the API client
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{t("teams.change_role_title")}</DialogTitle>
        <DialogDescription>
          {t("teams.change_role_text", { name: member.user.username })}
        </DialogDescription>
      </DialogHeader>
      <Field label={t("teams.role")} hint={t(`role_descriptions.${role}`)}>
        <RoleSelect value={role} onChange={setRole} />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={submitting || role === member.role}>
          {submitting && <Spinner />}
          {t("actions.change_role")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function ChangeRoleDialog({
  teamId,
  member,
  onOpenChange,
  onSaved,
}: {
  teamId: string;
  member: TeamMember | null;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const { t } = useI18n();
  return (
    <Dialog open={member !== null} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("common.close")}>
        {member && (
          <RoleForm
            key={member.user.id}
            teamId={teamId}
            member={member}
            onCancel={() => onOpenChange(false)}
            onSaved={() => {
              onOpenChange(false);
              onSaved();
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
