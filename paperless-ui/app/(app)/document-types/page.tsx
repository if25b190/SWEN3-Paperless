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

  const [name, setName] = useState(type?.name ?? "");
  const [description, setDescription] = useState(type?.description ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!name.trim()) next.name = "This field is required.";
    else if (name.trim().length > 100) next.name = `Must be at most 100 characters.`;
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const body = { name: name.trim(), description: description.trim() || null };
    setSubmitting(true);
    try {
      if (type) await documentTypesApi.update(type.id, body);
      else await documentTypesApi.create(body);
      toast.success((type ? "Document type updated." : "Document type created."));
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
        <DialogTitle>{(type ? "Edit document type" : "Add document type")}</DialogTitle>
      </DialogHeader>
      <Field label="Name" htmlFor="type-name" error={errors.name}>
        <Input
          id="type-name"
          value={name}
          maxLength={100}
          autoFocus
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(errors.name)}
        />
      </Field>
      <Field label="Description" htmlFor="type-description" optional error={errors.description}>
        <Textarea
          id="type-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </Field>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {(type ? "Save" : "Create")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export default function DocumentTypesPage() {
  const { data, loading, error, reload } = useQuery("document-types-page", () => documentTypesApi.list());
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
      toast.success("Document type deleted.");
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
      Add Document Type
    </Button>
  );

  return (
    <div className="grid gap-6">
      <PageHeader title="Document Types" description="Classify documents, for example invoices or contracts." actions={addButton} />

      <Card className="overflow-hidden p-0">
        {data === undefined && loading ? (
          <div className="grid gap-2 p-5">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-12" />
            ))}
          </div>
        ) : error ? (
          <EmptyState
            icon={<FolderTree className="size-6" />}
            title="Document types could not be loaded."
            description="Please try again."
            action={<Button variant="outline" onClick={reload}>Retry</Button>}
          />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon={<FolderTree className="size-6" />}
            title="No document types yet"
            description="Add a type to classify your documents."
            action={addButton}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Name</TableHead>
                <TableHead>Description</TableHead>
                <TableHead className="text-right">Actions</TableHead>
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
                        aria-label="Edit"
                        onClick={() => setDialog({ open: true, type })}
                      >
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label="Delete"
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
        <DialogContent closeLabel="Close">
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
        title="Delete document type?"
        description={`"${deleting?.name ?? ""}" will be deleted.`}
        confirmLabel="Delete"
        loading={deleteBusy}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
