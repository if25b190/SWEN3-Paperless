"use client";

import { useState } from "react";
import { FolderTree, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { ConfirmDialog, EmptyState, Field, PageHeader, Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Card, Skeleton, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input, Textarea } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { documentTypesApi } from "@/lib/api/services";
import { useI18n } from "@/lib/i18n/use-i18n";
import type { DocumentType } from "@/lib/types";
import { useQuery } from "@/lib/use-query";

function TypeForm({
  type,
  onCancel,
  onSaved,
}: {
  type: DocumentType | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const { t } = useI18n();
  const [name, setName] = useState(type?.name ?? "");
  const [description, setDescription] = useState(type?.description ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!name.trim()) next.name = t("validation.required");
    else if (name.trim().length > 100) next.name = t("validation.max_length", { max: 100 });
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const body = { name: name.trim(), description: description.trim() || null };
    setSubmitting(true);
    try {
      if (type) await documentTypesApi.update(type.id, body);
      else await documentTypesApi.create(body);
      toast.success(t(type ? "messages.type_updated" : "messages.type_created"));
      onSaved();
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <DialogHeader>
        <DialogTitle>{t(type ? "types.edit_title" : "types.create_title")}</DialogTitle>
      </DialogHeader>
      <Field label={t("types.field_name")} htmlFor="type-name" error={errors.name}>
        <Input
          id="type-name"
          value={name}
          maxLength={100}
          autoFocus
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(errors.name)}
        />
      </Field>
      <Field label={t("common.description")} htmlFor="type-description" optional error={errors.description}>
        <Textarea
          id="type-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {t(type ? "common.save" : "common.create")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export default function DocumentTypesPage() {
  const { t } = useI18n();
  const { data, reload } = useQuery("document-types-page", () => documentTypesApi.list());
  const [dialog, setDialog] = useState<{ open: boolean; type: DocumentType | null }>({
    open: false,
    type: null,
  });
  const [deleting, setDeleting] = useState<DocumentType | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await documentTypesApi.remove(deleting.id);
      toast.success(t("messages.type_deleted"));
      setDeleting(null);
      reload();
    } catch {
      // toast already shown by the API client
    } finally {
      setDeleteBusy(false);
    }
  }

  const addButton = (
    <Button onClick={() => setDialog({ open: true, type: null })}>
      <Plus />
      {t("actions.add_document_type")}
    </Button>
  );

  return (
    <div className="grid gap-6">
      <PageHeader title={t("types.title")} description={t("types.subtitle")} actions={addButton} />

      <Card className="overflow-hidden p-0">
        {data === undefined ? (
          <div className="grid gap-2 p-5">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-12" />
            ))}
          </div>
        ) : data.length === 0 ? (
          <EmptyState
            icon={<FolderTree className="size-6" />}
            title={t("types.empty_title")}
            description={t("types.empty_text")}
            action={addButton}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>{t("common.name")}</TableHead>
                <TableHead>{t("common.description")}</TableHead>
                <TableHead className="text-right">{t("common.actions")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((type) => (
                <TableRow key={type.id}>
                  <TableCell className="font-medium">{type.name}</TableCell>
                  <TableCell className="max-w-md truncate text-muted-foreground">
                    {type.description || "-"}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-0.5">
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={t("common.edit")}
                        onClick={() => setDialog({ open: true, type })}
                      >
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={t("common.delete")}
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => setDeleting(type)}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Dialog open={dialog.open} onOpenChange={(open) => setDialog((prev) => ({ ...prev, open }))}>
        <DialogContent closeLabel={t("common.close")}>
          <TypeForm
            key={dialog.type?.id ?? "new"}
            type={dialog.type}
            onCancel={() => setDialog((prev) => ({ ...prev, open: false }))}
            onSaved={() => {
              setDialog((prev) => ({ ...prev, open: false }));
              reload();
            }}
          />
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("types.delete_title")}
        description={t("types.delete_text", { name: deleting?.name ?? "" })}
        confirmLabel={t("common.delete")}
        loading={deleteBusy}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
