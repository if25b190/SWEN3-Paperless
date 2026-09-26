"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import {
  documentsApi,
  documentTypesApi,
  teamsApi,
} from "@/lib/api/client";
import {
  DocumentResponse,
  DocumentTypeResponse,
  TeamResponse,
  PageMetadata,
} from "@/lib/types/api";
import { StatusBadge } from "@/components/StatusBadge";
import { UploadDocumentDialog } from "@/components/UploadDocumentDialog";
import { EditDocumentDialog } from "@/components/EditDocumentDialog";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import {
  FileText,
  Upload,
  Search,
  MoreVertical,
  Download,
  Eye,
  Edit,
  Trash2,
  Users,
  Lock,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { toast } from "sonner";

export default function DashboardPage() {
  const { t } = useTranslation();
  const router = useRouter();

  // Data states
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [pagination, setPagination] = useState<PageMetadata>({
    page: 0,
    size: 10,
    total_elements: 0,
    total_pages: 0,
  });
  const [documentTypes, setDocumentTypes] = useState<DocumentTypeResponse[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);

  // Filter & sort states
  const [selectedDocType, setSelectedDocType] = useState<string>("all");
  const [selectedTeamFilter, setSelectedTeamFilter] = useState<string>("all");
  const [sortBy, setSortBy] = useState<string>("created_at,desc");
  const [localSearch, setLocalSearch] = useState<string>("");

  // Loading & dialog states
  const [loading, setLoading] = useState(true);
  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [editingDoc, setEditingDoc] = useState<DocumentResponse | null>(null);
  const [deletingDoc, setDeletingDoc] = useState<DocumentResponse | null>(null);
  const [deletingLoading, setDeletingLoading] = useState(false);

  // Polling ref
  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  const fetchDocuments = useCallback(
    async (showLoading = true) => {
      if (showLoading) setLoading(true);
      try {
        const docTypeId = selectedDocType === "all" ? undefined : selectedDocType;
        const res = await documentsApi.getDocuments({
          page: pagination.page,
          size: pagination.size,
          sort: sortBy,
          document_type_id: docTypeId,
        });

        let items = res.items || [];

        // Client-side visibility filter if selected
        if (selectedTeamFilter === "private") {
          items = items.filter((d) => !d.team_id);
        } else if (selectedTeamFilter !== "all") {
          items = items.filter((d) => d.team_id === selectedTeamFilter);
        }

        // Client-side quick filter on title/filename
        if (localSearch.trim()) {
          const q = localSearch.toLowerCase().trim();
          items = items.filter(
            (d) =>
              d.title.toLowerCase().includes(q) ||
              d.original_filename.toLowerCase().includes(q)
          );
        }

        setDocuments(items);
        setPagination(res.pagination);
      } catch {
        // Handled by api client
      } finally {
        if (showLoading) setLoading(false);
      }
    },
    [
      pagination.page,
      pagination.size,
      sortBy,
      selectedDocType,
      selectedTeamFilter,
      localSearch,
    ]
  );

  // Load document types and teams once
  useEffect(() => {
    documentTypesApi
      .getDocumentTypes()
      .then((res) => setDocumentTypes(res.items || []))
      .catch(() => {});

    teamsApi
      .getTeams()
      .then((res) => setTeams(res.items || []))
      .catch(() => {});
  }, []);

  // Fetch when filters or page change
  useEffect(() => {
    fetchDocuments(true);
  }, [fetchDocuments]);

  // Auto-polling when any document is in progress (PENDING, OCR_IN_PROGRESS, GENAI_IN_PROGRESS)
  useEffect(() => {
    const hasPending = documents.some(
      (doc) =>
        doc.status === "PENDING" ||
        doc.status === "OCR_IN_PROGRESS" ||
        doc.status === "GENAI_IN_PROGRESS"
    );

    if (hasPending) {
      pollTimerRef.current = setTimeout(() => {
        fetchDocuments(false);
      }, 3000);
    }

    return () => {
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, [documents, fetchDocuments]);

  const handleDelete = async () => {
    if (!deletingDoc) return;
    setDeletingLoading(true);
    try {
      await documentsApi.deleteDocument(deletingDoc.id);
      toast.success(t.documents.deleteSuccess);
      setDeletingDoc(null);
      fetchDocuments(false);
    } catch {
      // Handled by client
    } finally {
      setDeletingLoading(false);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (!bytes || bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  };

  const formatDate = (isoString: string) => {
    try {
      return new Date(isoString).toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return isoString;
    }
  };

  const getTeamName = (teamId?: string | null) => {
    if (!teamId) return null;
    const team = teams.find((tm) => tm.id === teamId);
    return team ? team.name : "Team";
  };

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        {/* Top Header & Action */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              {t.documents.title}
            </h1>
            <p className="text-sm text-slate-500">{t.documents.subtitle}</p>
          </div>
          <Button
            onClick={() => setUploadDialogOpen(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white gap-2 font-medium"
          >
            <Upload className="h-4 w-4" />
            {t.documents.uploadDocument}
          </Button>
        </div>

        {/* Filters & Search Toolbar */}
        <Card className="border-slate-200 shadow-xs bg-white">
          <CardContent className="p-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {/* Instant filter input */}
              <div className="relative">
                <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                <Input
                  placeholder="Filter title or filename..."
                  value={localSearch}
                  onChange={(e) => setLocalSearch(e.target.value)}
                  className="pl-9 h-9"
                />
              </div>

              {/* Document Type Filter */}
              <Select
                value={selectedDocType}
                onValueChange={(val) => {
                  setSelectedDocType(val);
                  setPagination((prev) => ({ ...prev, page: 0 }));
                }}
              >
                <SelectTrigger className="h-9">
                  <SelectValue placeholder={t.documents.filterByType} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t.documents.filterByType}</SelectItem>
                  {documentTypes.map((dt) => (
                    <SelectItem key={dt.id} value={dt.id}>
                      {dt.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              {/* Team / Visibility Filter */}
              <Select
                value={selectedTeamFilter}
                onValueChange={(val) => {
                  setSelectedTeamFilter(val);
                  setPagination((prev) => ({ ...prev, page: 0 }));
                }}
              >
                <SelectTrigger className="h-9">
                  <SelectValue placeholder={t.documents.filterByTeam} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t.documents.filterByTeam}</SelectItem>
                  <SelectItem value="private">{t.common.private}</SelectItem>
                  {teams.map((tm) => (
                    <SelectItem key={tm.id} value={tm.id}>
                      {tm.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              {/* Sorting */}
              <Select
                value={sortBy}
                onValueChange={(val) => {
                  setSortBy(val);
                  setPagination((prev) => ({ ...prev, page: 0 }));
                }}
              >
                <SelectTrigger className="h-9">
                  <SelectValue placeholder={t.documents.sortBy} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="created_at,desc">
                    {t.documents.sortNewest}
                  </SelectItem>
                  <SelectItem value="created_at,asc">
                    {t.documents.sortOldest}
                  </SelectItem>
                  <SelectItem value="title,asc">
                    {t.documents.sortTitleAsc}
                  </SelectItem>
                  <SelectItem value="title,desc">
                    {t.documents.sortTitleDesc}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>

        {/* Documents Listing */}
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-2" />
            <p className="text-sm text-slate-500">{t.common.loading}</p>
          </div>
        ) : documents.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200 text-center">
            <div className="h-12 w-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
              <FileText className="h-6 w-6" />
            </div>
            <h3 className="text-base font-semibold text-slate-900">
              {t.documents.noDocumentsFound}
            </h3>
            <p className="text-sm text-slate-500 max-w-sm mt-1 mb-4">
              {t.documents.noDocumentsSubtitle}
            </p>
            <Button
              onClick={() => setUploadDialogOpen(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white gap-2 font-medium"
            >
              <Upload className="h-4 w-4" />
              {t.documents.uploadDocument}
            </Button>
          </div>
        ) : (
          <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  <tr>
                    <th className="py-3.5 px-4">{t.documents.docTitle}</th>
                    <th className="py-3.5 px-4">{t.documents.docType}</th>
                    <th className="py-3.5 px-4">{t.documents.teamSharing}</th>
                    <th className="py-3.5 px-4">{t.common.status}</th>
                    <th className="py-3.5 px-4">{t.documents.fileSize}</th>
                    <th className="py-3.5 px-4">{t.documents.createdAt}</th>
                    <th className="py-3.5 px-4 text-right">{t.common.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {documents.map((doc) => {
                    const teamName = getTeamName(doc.team_id);
                    return (
                      <tr
                        key={doc.id}
                        className="hover:bg-slate-50/75 transition-colors group"
                      >
                        <td className="py-3.5 px-4">
                          <Link
                            href={`/documents/${doc.id}`}
                            className="font-medium text-slate-900 hover:text-blue-600 flex items-center gap-2 group-hover:underline"
                          >
                            <FileText className="h-4 w-4 text-slate-400 shrink-0" />
                            <span className="truncate max-w-xs">{doc.title}</span>
                          </Link>
                          <span className="text-xs text-slate-400 block truncate max-w-xs pl-6">
                            {doc.original_filename}
                          </span>
                        </td>
                        <td className="py-3.5 px-4">
                          {doc.document_type ? (
                            <Badge variant="outline" className="font-normal text-xs">
                              {doc.document_type.name}
                            </Badge>
                          ) : (
                            <span className="text-xs text-slate-400">-</span>
                          )}
                        </td>
                        <td className="py-3.5 px-4">
                          {teamName ? (
                            <span className="inline-flex items-center gap-1.5 text-xs text-slate-700 font-medium">
                              <Users className="h-3.5 w-3.5 text-blue-500" />
                              {teamName}
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 text-xs text-slate-500">
                              <Lock className="h-3.5 w-3.5 text-slate-400" />
                              {t.common.private}
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4">
                          <StatusBadge status={doc.status} />
                        </td>
                        <td className="py-3.5 px-4 text-xs text-slate-500 whitespace-nowrap">
                          {formatFileSize(doc.file_size)}
                        </td>
                        <td className="py-3.5 px-4 text-xs text-slate-500 whitespace-nowrap">
                          {formatDate(doc.created_at)}
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8 text-slate-500 hover:text-slate-900"
                              >
                                <MoreVertical className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="w-44">
                              <DropdownMenuItem
                                onClick={() =>
                                  router.push(`/documents/${doc.id}`)
                                }
                                className="gap-2 cursor-pointer"
                              >
                                <Eye className="h-4 w-4 text-slate-500" />
                                <span>{t.documents.viewDetails}</span>
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onClick={() => {
                                  documentsApi.downloadDocument(
                                    doc.id,
                                    doc.original_filename || `${doc.title}.pdf`
                                  );
                                }}
                                className="gap-2 cursor-pointer"
                              >
                                <Download className="h-4 w-4 text-slate-500" />
                                <span>{t.common.download}</span>
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onClick={() => setEditingDoc(doc)}
                                className="gap-2 cursor-pointer"
                              >
                                <Edit className="h-4 w-4 text-slate-500" />
                                <span>{t.common.edit}</span>
                              </DropdownMenuItem>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                onClick={() => setDeletingDoc(doc)}
                                className="gap-2 text-red-600 focus:text-red-700 focus:bg-red-50 cursor-pointer"
                              >
                                <Trash2 className="h-4 w-4" />
                                <span>{t.common.delete}</span>
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {pagination.total_pages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-slate-200 bg-slate-50 text-xs text-slate-500">
                <div>
                  {t.common.page} {pagination.page + 1} {t.common.of}{" "}
                  {pagination.total_pages} ({pagination.total_elements}{" "}
                  {t.common.total})
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pagination.page <= 0}
                    onClick={() =>
                      setPagination((prev) => ({
                        ...prev,
                        page: prev.page - 1,
                      }))
                    }
                  >
                    {t.common.paginationPrev}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pagination.page >= pagination.total_pages - 1}
                    onClick={() =>
                      setPagination((prev) => ({
                        ...prev,
                        page: prev.page + 1,
                      }))
                    }
                  >
                    {t.common.paginationNext}
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Upload Dialog */}
        <UploadDocumentDialog
          open={uploadDialogOpen}
          onOpenChange={setUploadDialogOpen}
          onSuccess={() => fetchDocuments(false)}
        />

        {/* Edit Metadata Dialog */}
        <EditDocumentDialog
          document={editingDoc}
          open={!!editingDoc}
          onOpenChange={(open) => !open && setEditingDoc(null)}
          onSuccess={() => fetchDocuments(false)}
        />

        {/* Delete Confirmation Dialog */}
        <ConfirmDialog
          open={!!deletingDoc}
          onOpenChange={(open) => !open && setDeletingDoc(null)}
          title={t.documents.deleteConfirmTitle}
          description={t.documents.deleteConfirmText}
          confirmLabel={t.common.delete}
          loading={deletingLoading}
          onConfirm={handleDelete}
        />
      </div>
    </ProtectedRoute>
  );
}
