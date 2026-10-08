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
import { useI18n } from "@/lib/i18n/use-i18n";
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
  const { t } = useI18n();
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
        title={t("documents.preview_failed")}
      />
    );
  }
  if (!url) return <Skeleton className="h-[70vh] w-full" />;
  return (
    <iframe
      src={url}
      title={t("documents.preview")}
      className="h-[70vh] w-full rounded-lg border bg-muted"
    />
  );
}

export default function DocumentDetailPage() {
  const { t, locale } = useI18n();
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
          title={t("documents.detail_not_found")}
          action={
            <Button asChild variant="outline">
              <Link href="/">
                <ArrowLeft />
                {t("documents.back_to_documents")}
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
      : (users.data?.find((u) => u.id === doc.owner_id)?.username ?? t("common.unknown"));
  const teamName = doc.team_id
    ? (teamById.get(doc.team_id)?.name ?? t("documents.team_unknown"))
    : null;

  async function onDelete() {
    setDeleteBusy(true);
    try {
      await documentsApi.remove(id);
      toast.success(t("messages.document_deleted"));
      router.replace("/");
    } catch {
      setDeleteBusy(false);
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(t("common.copied"));
    } catch {
      toast.error(t("errors.unknown"));
    }
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <Button asChild variant="ghost" size="sm" className="-ml-3 mb-1">
            <Link href="/">
              <ArrowLeft />
              {t("documents.back_to_documents")}
            </Link>
          </Button>
          <h1 className="truncate text-2xl font-semibold tracking-tight">{doc.title}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <StatusBadge status={doc.status} />
            {doc.document_type && <Badge variant="secondary">{doc.document_type.name}</Badge>}
            {teamName ? (
              <Badge variant="outline">{teamName}</Badge>
            ) : (
              <Badge variant="muted">{t("documents.private")}</Badge>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => void downloadDocument(doc).catch(() => undefined)}>
            <Download />
            {t("actions.download")}
          </Button>
          {writable && (
            <>
              <Button variant="outline" onClick={() => setEditing(true)}>
                <Pencil />
                {t("actions.edit_metadata")}
              </Button>
              <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
                <Trash2 />
                {t("common.delete")}
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
                {t("documents.tab_summary")}
              </TabsTrigger>
              <TabsTrigger value="ocr">
                <TextSelect className="size-4" />
                {t("documents.tab_ocr")}
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
                      emptyKey="documents.summary_empty"
                      processingKey="documents.summary_processing"
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
                          {t("common.copy")}
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
                      emptyKey="documents.ocr_empty"
                      processingKey="documents.ocr_processing"
                    />
                  )}
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>{t("documents.metadata")}</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              <MetaRow label={t("documents.meta_owner")}>{ownerName}</MetaRow>
              <MetaRow label={t("documents.meta_team")}>
                {teamName ? (
                  teamName
                ) : (
                  <span className="text-muted-foreground">{t("documents.private")}</span>
                )}
              </MetaRow>
              <MetaRow label={t("documents.meta_type")}>
                {doc.document_type?.name ?? <span className="text-muted-foreground">-</span>}
              </MetaRow>
              <MetaRow label={t("documents.meta_filename")}>{doc.original_filename}</MetaRow>
              <MetaRow label={t("documents.meta_size")}>{formatBytes(doc.file_size)}</MetaRow>
              <MetaRow label={t("documents.meta_created")}>{formatDate(doc.created_at, locale)}</MetaRow>
              <MetaRow label={t("documents.meta_updated")}>{formatDate(doc.updated_at, locale)}</MetaRow>
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
        title={t("documents.delete_title")}
        description={t("documents.delete_text", { title: doc.title })}
        confirmLabel={t("common.delete")}
        loading={deleteBusy}
        onConfirm={onDelete}
      />
    </div>
  );
}

function ProcessingHint({
  processing,
  failed,
  emptyKey,
  processingKey,
}: {
  processing: boolean;
  failed: boolean;
  emptyKey: string;
  processingKey: string;
}) {
  const { t } = useI18n();
  if (failed) return <p className="text-sm text-destructive">{t("documents.processing_failed")}</p>;
  if (processing) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Spinner />
        {t(processingKey)}
      </p>
    );
  }
  return <p className="text-sm text-muted-foreground">{t(emptyKey)}</p>;
}
