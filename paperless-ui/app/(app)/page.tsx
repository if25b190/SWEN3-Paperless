"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Download, Eye, FileText, Pencil, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { isProcessing, StatusBadge } from "@/components/badges";
import { ConfirmDialog, EmptyState, PageHeader, Pagination, Spinner } from "@/components/common";
import { EditDocumentDialog } from "@/components/documents/edit-dialog";
import { UploadDocumentDialog } from "@/components/documents/upload-dialog";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Badge, Card, Skeleton, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { documentsApi } from "@/lib/api/services";
import { downloadDocument } from "@/lib/documents";
import { canWriteDocument, useDocumentTypes, useMemberships } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n/use-i18n";
import type { DocumentItem } from "@/lib/types";
import { useQuery } from "@/lib/use-query";
import { formatBytes, formatDay } from "@/lib/utils";

const PAGE_SIZE = 10;
const POLL_INTERVAL_MS = 4000;
const ALL = "__all__";
const PRIVATE = "__private__";

const SORT_OPTIONS = [
  { value: "created_at,desc", key: "documents.sort_created_desc" },
  { value: "created_at,asc", key: "documents.sort_created_asc" },
  { value: "updated_at,desc", key: "documents.sort_updated_desc" },
  { value: "title,asc", key: "documents.sort_title_asc" },
  { value: "title,desc", key: "documents.sort_title_desc" },
];

function IconAction({
  label,
  onClick,
  children,
  destructive,
}: {
  label: string;
  onClick: () => void;
  children: React.ReactNode;
  destructive?: boolean;
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          aria-label={label}
          onClick={onClick}
          className={destructive ? "text-destructive hover:bg-destructive/10 hover:text-destructive" : ""}
        >
          {children}
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}

export default function DashboardPage() {
  const { t, locale } = useI18n();
  const { user } = useAuth();
  const { types } = useDocumentTypes();
  const { roleByTeam, teamById, memberships } = useMemberships();

  const [page, setPage] = useState(0);
  const [typeFilter, setTypeFilter] = useState(ALL);
  const [scope, setScope] = useState(ALL);
  const [sort, setSort] = useState(SORT_OPTIONS[0].value);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState<DocumentItem | null>(null);
  const [deleting, setDeleting] = useState<DocumentItem | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const { data, loading, reload } = useQuery(`documents:${page}:${typeFilter}:${sort}`, () =>
    documentsApi.list({
      page,
      size: PAGE_SIZE,
      sort,
      document_type_id: typeFilter === ALL ? undefined : typeFilter,
    }),
  );

  const hasProcessing = data?.items.some((doc) => isProcessing(doc.status)) ?? false;

  useEffect(() => {
    if (!hasProcessing) return;
    const timer = setInterval(reload, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [hasProcessing, reload]);

  const items = useMemo(() => {
    const all = data?.items ?? [];
    if (scope === ALL) return all;
    if (scope === PRIVATE) return all.filter((doc) => !doc.team_id);
    return all.filter((doc) => doc.team_id === scope);
  }, [data, scope]);

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await documentsApi.remove(deleting.id);
      toast.success(t("messages.document_deleted"));
      setDeleting(null);
      reload();
    } catch {
      // toast already shown by the API client
    } finally {
      setDeleteBusy(false);
    }
  }

  async function onDownload(doc: DocumentItem) {
    try {
      await downloadDocument(doc);
    } catch {
      // toast already shown by the API client
    }
  }

  const initialLoading = data === undefined && loading;
  const totalPages = data?.pagination.total_pages ?? 0;

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("documents.title")}
        description={t("documents.subtitle")}
        actions={
          <Button onClick={() => setUploadOpen(true)}>
            <Upload />
            {t("actions.upload_doc")}
          </Button>
        }
      />

      <Card className="p-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="grid gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">{t("documents.filter_type")}</span>
            <Select
              value={typeFilter}
              onValueChange={(value) => {
                setTypeFilter(value);
                setPage(0);
              }}
            >
              <SelectTrigger aria-label={t("documents.filter_type")}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t("documents.all_types")}</SelectItem>
                {types.map((type) => (
                  <SelectItem key={type.id} value={type.id}>
                    {type.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">{t("documents.filter_scope")}</span>
            <Select value={scope} onValueChange={setScope}>
              <SelectTrigger aria-label={t("documents.filter_scope")}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t("documents.scope_all")}</SelectItem>
                <SelectItem value={PRIVATE}>{t("documents.scope_private")}</SelectItem>
                {memberships.map(({ team }) => (
                  <SelectItem key={team.id} value={team.id}>
                    {t("documents.scope_team", { name: team.name })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">{t("documents.filter_sort")}</span>
            <Select
              value={sort}
              onValueChange={(value) => {
                setSort(value);
                setPage(0);
              }}
            >
              <SelectTrigger aria-label={t("documents.filter_sort")}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SORT_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {t(option.key)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        {scope !== ALL && (
          <p className="mt-2 text-xs text-muted-foreground">{t("documents.scope_note")}</p>
        )}
      </Card>

      <Card className="overflow-hidden p-0">
        {initialLoading ? (
          <div className="grid gap-3 p-5">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-12 w-full" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            icon={<FileText className="size-6" />}
            title={t("documents.empty_title")}
            description={t("documents.empty_text")}
            action={
              <Button onClick={() => setUploadOpen(true)}>
                <Upload />
                {t("actions.upload_doc")}
              </Button>
            }
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>{t("documents.col_title")}</TableHead>
                  <TableHead>{t("documents.col_type")}</TableHead>
                  <TableHead>{t("documents.col_status")}</TableHead>
                  <TableHead>{t("documents.col_sharing")}</TableHead>
                  <TableHead>{t("documents.col_size")}</TableHead>
                  <TableHead>{t("documents.col_created")}</TableHead>
                  <TableHead className="text-right">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((doc) => {
                  const writable = user ? canWriteDocument(doc, user.id, roleByTeam) : false;
                  const teamName = doc.team_id
                    ? (teamById.get(doc.team_id)?.name ?? t("documents.team_unknown"))
                    : null;
                  return (
                    <TableRow key={doc.id}>
                      <TableCell className="max-w-xs">
                        <Link
                          href={`/documents/${doc.id}`}
                          className="block truncate font-medium hover:text-primary hover:underline"
                        >
                          {doc.title}
                        </Link>
                        <span className="block truncate text-xs text-muted-foreground">
                          {doc.original_filename}
                        </span>
                      </TableCell>
                      <TableCell>
                        {doc.document_type ? (
                          <Badge variant="secondary">{doc.document_type.name}</Badge>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={doc.status} />
                      </TableCell>
                      <TableCell>
                        {teamName ? (
                          <Badge variant="outline">{teamName}</Badge>
                        ) : (
                          <Badge variant="muted">{t("documents.private")}</Badge>
                        )}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatBytes(doc.file_size)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatDay(doc.created_at, locale)}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-0.5">
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button variant="ghost" size="icon" asChild aria-label={t("actions.preview")}>
                                <Link href={`/documents/${doc.id}`}>
                                  <Eye />
                                </Link>
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>{t("actions.preview")}</TooltipContent>
                          </Tooltip>
                          <IconAction label={t("actions.download")} onClick={() => void onDownload(doc)}>
                            <Download />
                          </IconAction>
                          {writable && (
                            <>
                              <IconAction label={t("actions.edit_metadata")} onClick={() => setEditing(doc)}>
                                <Pencil />
                              </IconAction>
                              <IconAction
                                label={t("actions.delete_document")}
                                onClick={() => setDeleting(doc)}
                                destructive
                              >
                                <Trash2 />
                              </IconAction>
                            </>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </>
        )}
        {loading && data !== undefined && (
          <div className="flex items-center gap-2 border-t px-4 py-2 text-xs text-muted-foreground">
            <Spinner className="size-3" />
            {t("common.loading")}
          </div>
        )}
      </Card>

      <UploadDocumentDialog
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        onUploaded={() => {
          setPage(0);
          reload();
        }}
      />
      <EditDocumentDialog
        document={editing}
        onOpenChange={(open) => !open && setEditing(null)}
        onSaved={() => reload()}
      />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("documents.delete_title")}
        description={t("documents.delete_text", { title: deleting?.title ?? "" })}
        confirmLabel={t("common.delete")}
        loading={deleteBusy}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
