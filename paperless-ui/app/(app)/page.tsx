"use client";

import { useEffect, useMemo, useState } from "react";
import { Download, FileText, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { isProcessing, StatusBadge } from "@/components/badges";
import { ConfirmDialog, EmptyState, PageHeader, Pagination, Spinner } from "@/components/common";
import { UploadDocumentDialog } from "@/components/documents/upload-dialog";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { Badge, Card, Skeleton, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { documentsApi } from "@/lib/api/services";
import { downloadDocument } from "@/lib/documents";
import { canWriteDocument, useDocumentTypes, useMemberships } from "@/lib/hooks";
import type { DocumentItem } from "@/lib/types";
import { useQuery } from "@/lib/use-query";
import { formatBytes, formatDay } from "@/lib/utils";

const PAGE_SIZE = 10;
const POLL_INTERVAL_MS = 4000;
const ALL = "__all__";
const PRIVATE = "__private__";

const SORT_OPTIONS = [
  { value: "created_at,desc", label: "Newest first" },
  { value: "created_at,asc", label: "Oldest first" },
  { value: "updated_at,desc", label: "Recently updated" },
  { value: "title,asc", label: "Title A to Z" },
  { value: "title,desc", label: "Title Z to A" },
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
  const { user } = useAuth();
  const { types } = useDocumentTypes();
  const { roleByTeam, teamById, memberships } = useMemberships();

  const [page, setPage] = useState(0);
  const [typeFilter, setTypeFilter] = useState(ALL);
  const [scope, setScope] = useState(ALL);
  const [sort, setSort] = useState(SORT_OPTIONS[0].value);

  const [uploadOpen, setUploadOpen] = useState(false);
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
      toast.success("Document deleted.");
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
        title="Documents"
        description="Browse, filter and manage your documents."
        actions={
          <Button onClick={() => setUploadOpen(true)}>
            <Upload />
            Upload Document
          </Button>
        }
      />

      <Card className="p-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="grid gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">Document type</span>
            <Select
              value={typeFilter}
              onValueChange={(value) => {
                setTypeFilter(value);
                setPage(0);
              }}
            >
              <SelectTrigger aria-label="Document type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All types</SelectItem>
                {types.map((type) => (
                  <SelectItem key={type.id} value={type.id}>
                    {type.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">Sharing</span>
            <Select value={scope} onValueChange={setScope}>
              <SelectTrigger aria-label="Sharing">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All documents</SelectItem>
                <SelectItem value={PRIVATE}>Private only</SelectItem>
                {memberships.map(({ team }) => (
                  <SelectItem key={team.id} value={team.id}>
                    {`Team: ${team.name}`}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">Sort by</span>
            <Select
              value={sort}
              onValueChange={(value) => {
                setSort(value);
                setPage(0);
              }}
            >
              <SelectTrigger aria-label="Sort by">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SORT_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        {scope !== ALL && (
          <p className="mt-2 text-xs text-muted-foreground">Sharing filter applies to the current page.</p>
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
            title="No documents found"
            description="Upload your first PDF to get started."
            action={
              <Button onClick={() => setUploadOpen(true)}>
                <Upload />
                Upload Document
              </Button>
            }
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>Title</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Sharing</TableHead>
                  <TableHead>Size</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((doc) => {
                  const writable = user ? canWriteDocument(doc, user.id, roleByTeam) : false;
                  const teamName = doc.team_id
                    ? (teamById.get(doc.team_id)?.name ?? "Team")
                    : null;
                  return (
                    <TableRow key={doc.id}>
                      <TableCell className="max-w-xs">
                        <span
                          className="block truncate font-medium"
                        >
                          {doc.title}
                        </span>
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
                          <Badge variant="muted">Private</Badge>
                        )}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatBytes(doc.file_size)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatDay(doc.created_at)}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-0.5">
                          <IconAction label="Download" onClick={() => void onDownload(doc)}>
                            <Download />
                          </IconAction>
                          {writable && (
                            <>
                              <IconAction
                                label="Delete Document"
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
            Loading...
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
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete document?"
        description={`"${deleting?.title ?? ""}" will be permanently deleted, including its file and search index entry.`}
        confirmLabel="Delete"
        loading={deleteBusy}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
