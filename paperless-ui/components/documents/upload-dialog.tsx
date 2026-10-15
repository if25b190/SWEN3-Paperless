"use client";

import { useRef, useState } from "react";
import { FileUp, X } from "lucide-react";
import { toast } from "sonner";
import { Field, Spinner } from "@/components/common";
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
import { cn, formatBytes } from "@/lib/utils";

function UploadForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const { types } = useDocumentTypes();
  const { writableTeams } = useMemberships();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [typeId, setTypeId] = useState(NONE_VALUE);
  const [teamId, setTeamId] = useState(PRIVATE_VALUE);
  const [dragging, setDragging] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  function chooseFile(candidate: File | undefined) {
    if (!candidate) return;
    const isPdf =
      candidate.type === "application/pdf" || candidate.name.toLowerCase().endsWith(".pdf");
    if (!isPdf) {
      setErrors((prev) => ({ ...prev, document: "Only PDF files are supported." }));
      return;
    }
    setErrors((prev) => ({ ...prev, document: "" }));
    setFile(candidate);
    if (!title.trim()) setTitle(candidate.name.replace(/\.pdf$/i, ""));
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!file) next.document = "Please choose a PDF file.";
    if (!title.trim()) next.title = "This field is required.";
    else if (title.trim().length > 255) next.title = `Must be at most 255 characters.`;
    setErrors(next);
    if (Object.keys(next).length > 0 || !file) return;

    setSubmitting(true);
    try {
      await documentsApi.upload({
        file,
        title: title.trim(),
        documentTypeId: typeId === NONE_VALUE ? undefined : typeId,
        teamId: teamId === PRIVATE_VALUE ? undefined : teamId,
      });
      toast.success("Document uploaded.");
      onDone();
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-4" noValidate>
      <DialogHeader>
        <DialogTitle>Upload document</DialogTitle>
        <DialogDescription>Add a PDF. Text extraction and AI summary start automatically.</DialogDescription>
      </DialogHeader>

      <Field label="PDF" error={errors.document}>
        {file ? (
          <div className="flex items-center gap-3 rounded-lg border bg-muted/40 p-3">
            <FileUp className="size-5 text-primary" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{file.name}</p>
              <p className="text-xs text-muted-foreground">{formatBytes(file.size)}</p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Remove file"
              onClick={() => setFile(null)}
            >
              <X />
            </Button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              chooseFile(event.dataTransfer.files[0]);
            }}
            className={cn(
              "flex flex-col items-center gap-2 rounded-lg border-2 border-dashed px-4 py-8 text-center transition-colors hover:border-primary/60 hover:bg-accent/50",
              dragging && "border-primary bg-accent",
              errors.document && "border-destructive",
            )}
          >
            <FileUp className="size-8 text-primary" />
            <span className="text-sm font-medium">Drag and drop a PDF here</span>
            <span className="text-xs text-muted-foreground">or click to browse</span>
          </button>
        )}
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf,.pdf"
          className="hidden"
          onChange={(event) => {
            chooseFile(event.target.files?.[0]);
            event.target.value = "";
          }}
        />
      </Field>

      <Field label="Title" htmlFor="doc-title" error={errors.title}>
        <Input
          id="doc-title"
          value={title}
          maxLength={255}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={Boolean(errors.title)}
        />
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Document type"
          optional
          error={errors.document_type_id}
        >
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
          hint="Only teams where you can write are listed."
          error={errors.team_id}
        >
          <Select value={teamId} onValueChange={setTeamId}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={PRIVATE_VALUE}>Private (only me)</SelectItem>
              {writableTeams.map((team) => (
                <SelectItem key={team.id} value={team.id}>
                  {team.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting && <Spinner />}
          {submitting ? "Uploading..." : "Upload"}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function UploadDocumentDialog({
  open,
  onOpenChange,
  onUploaded,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onUploaded: () => void;
}) {

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel="Close">
        <UploadForm
          onCancel={() => onOpenChange(false)}
          onDone={() => {
            onOpenChange(false);
            onUploaded();
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
