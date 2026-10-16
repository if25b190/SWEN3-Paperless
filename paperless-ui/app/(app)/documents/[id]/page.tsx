"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Copy, Download, FileX, Pencil, Sparkles, TextSelect, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { isProcessing, StatusBadge } from "@/components/badges";
import { ConfirmDialog, EmptyState, Spinner } from "@/components/common";
import { EditDocumentDialog } from "@/components/documents/edit-dialog";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Badge, Card, CardContent, CardHeader, CardTitle, Skeleton } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { documentsApi, usersApi } from "@/lib/api/services";
import { downloadDocument } from "@/lib/documents";
import { canWriteDocument, useMemberships } from "@/lib/hooks";
import { useQuery } from "@/lib/use-query";
import { formatBytes, formatDate } from "@/lib/utils";

const POLL_INTERVAL_MS = 4000;

function MetaRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-0.5 py-2.5">
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="text-sm break-words">{children}</dd>
    </div>
  );
}

function PdfPreview({ id }: { id: string }) {
  const query = useQuery(`pdf:${id}`, async () =>
    URL.createObjectURL(await documentsApi.download(id, { silent: true })),
  );
  const url = query.data;

  useEffect(() => {
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  }, [url]);

  if (query.error) {
    return (
      <EmptyState
        icon={<FileX className="size-6" />}
        title="The preview could not be loaded."
      />
    );
  }
  if (!url) return <Skeleton className="h-[70vh] w-full" />;
  return (
    <iframe
      src={url}
      title="PDF preview"
      className="h-[70vh] w-full rounded-lg border bg-muted"
    />
  );
}

export default function DocumentDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { user } = useAuth();
  const { roleByTeam, teamById } = useMemberships();

  const { data: doc, error, reload } = useQuery(`document:${id}`, () =>
    documentsApi.get(id),
  );
  const users = useQuery("users", () => usersApi.list({ silent: true }));

  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const processing = doc ? isProcessing(doc.status) : false;
  useEffect(() => {
    if (!processing) return;
    const timer = setInterval(reload, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [processing, reload]);

  if (error && !doc) {
    return (
      <Card>
        <EmptyState
          icon={<FileX className="size-6" />}
          title="This document could not be loaded."
          action={
            <Button asChild variant="outline">
              <Link href="/">
                <ArrowLeft />
                Back to documents
              </Link>
            </Button>
          }
        />
      </Card>
    );
  }

  if (!doc) {
    return (
      <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
        <Skeleton className="h-[70vh]" />
        <Skeleton className="h-96" />
      </div>
    );
  }

  const writable = user ? canWriteDocument(doc, user.id, roleByTeam) : false;
  const ownerName =
    doc.owner_id === user?.id
      ? user.username
      : (users.data?.find((u) => u.id === doc.owner_id)?.username ?? "Unknown");
  const teamName = doc.team_id
    ? (teamById.get(doc.team_id)?.name ?? "Team")
    : null;

  async function onDelete() {
    setDeleteBusy(true);
    try {
      await documentsApi.remove(id);
      toast.success("Document deleted.");
      router.replace("/");
    } catch {
      setDeleteBusy(false);
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      toast.success("Copied to clipboard.");
    } catch {
      toast.error("Something went wrong.");
    }
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <Button asChild variant="ghost" size="sm" className="-ml-3 mb-1">
            <Link href="/">
              <ArrowLeft />
              Back to documents
            </Link>
          </Button>
          <h1 className="truncate text-2xl font-semibold tracking-tight">{doc.title}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <StatusBadge status={doc.status} />
            {doc.document_type && <Badge variant="secondary">{doc.document_type.name}</Badge>}
            {teamName ? (
              <Badge variant="outline">{teamName}</Badge>
            ) : (
              <Badge variant="muted">Private</Badge>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => void downloadDocument(doc).catch(() => undefined)}>
            <Download />
            Download
          </Button>
          {writable && (
            <>
              <Button variant="outline" onClick={() => setEditing(true)}>
                <Pencil />
                Edit Metadata
              </Button>
              <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
                <Trash2 />
                Delete
              </Button>
            </>
          )}
        </div>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="grid gap-6">
          <Card className="p-3">
            <PdfPreview id={doc.id} />
          </Card>

          <Tabs defaultValue="summary">
            <TabsList>
              <TabsTrigger value="summary">
                <Sparkles className="size-4" />
                AI Summary
              </TabsTrigger>
              <TabsTrigger value="ocr">
                <TextSelect className="size-4" />
                Extracted Text
              </TabsTrigger>
            </TabsList>
            <TabsContent value="summary">
              <Card>
                <CardContent className="pt-5">
                  {doc.summary ? (
                    <p className="text-sm leading-relaxed whitespace-pre-wrap">{doc.summary}</p>
                  ) : (
                    <ProcessingHint
                      processing={processing}
                      failed={doc.status === "FAILED"}
                      emptyMessage="No summary available yet."
                      processingMessage="The summary is still being generated."
                    />
                  )}
                </CardContent>
              </Card>
            </TabsContent>
            <TabsContent value="ocr">
              <Card>
                <CardContent className="pt-5">
                  {doc.ocr_content ? (
                    <div className="grid gap-3">
                      <div className="flex justify-end">
                        <Button variant="outline" size="sm" onClick={() => void copy(doc.ocr_content ?? "")}>
                          <Copy />
                          Copy
                        </Button>
                      </div>
                      <pre className="max-h-96 overflow-auto rounded-lg bg-muted p-4 text-sm leading-relaxed whitespace-pre-wrap">
                        {doc.ocr_content}
                      </pre>
                    </div>
                  ) : (
                    <ProcessingHint
                      processing={processing}
                      failed={doc.status === "FAILED"}
                      emptyMessage="No extracted text available yet."
                      processingMessage="Text extraction is still running."
                    />
                  )}
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              <MetaRow label="Owner">{ownerName}</MetaRow>
              <MetaRow label="Team">
                {teamName ? (
                  teamName
                ) : (
                  <span className="text-muted-foreground">Private</span>
                )}
              </MetaRow>
              <MetaRow label="Type">
                {doc.document_type?.name ?? <span className="text-muted-foreground">-</span>}
              </MetaRow>
              <MetaRow label="File name">{doc.original_filename}</MetaRow>
              <MetaRow label="File size">{formatBytes(doc.file_size)}</MetaRow>
              <MetaRow label="Created">{formatDate(doc.created_at)}</MetaRow>
              <MetaRow label="Updated">{formatDate(doc.updated_at)}</MetaRow>
            </dl>
          </CardContent>
        </Card>
      </div>

      <EditDocumentDialog
        document={editing ? doc : null}
        onOpenChange={setEditing}
        onSaved={() => reload()}
      />
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete document?"
        description={`"${doc.title}" will be permanently deleted, including its file and search index entry.`}
        confirmLabel="Delete"
        loading={deleteBusy}
        onConfirm={onDelete}
      />
    </div>
  );
}

function ProcessingHint({
  processing,
  failed,
  emptyMessage,
  processingMessage,
}: {
  processing: boolean;
  failed: boolean;
  emptyMessage: string;
  processingMessage: string;
}) {

  if (failed) return <p className="text-sm text-destructive">Processing failed for this document.</p>;
  if (processing) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Spinner />
        {processingMessage}
      </p>
    );
  }
  return <p className="text-sm text-muted-foreground">{emptyMessage}</p>;
}
