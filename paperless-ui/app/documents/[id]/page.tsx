"use client";

import React, { useState, useEffect, useCallback, useRef, use } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import { useAuth } from "@/lib/auth/context";
import { documentsApi, teamsApi } from "@/lib/api/client";
import { DocumentResponse, TeamResponse } from "@/lib/types/api";
import { StatusBadge } from "@/components/StatusBadge";
import { EditDocumentDialog } from "@/components/EditDocumentDialog";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import {
  FileText,
  ArrowLeft,
  Download,
  Edit,
  Trash2,
  Copy,
  Check,
  Sparkles,
  AlignLeft,
  Users,
  Lock,
  Calendar,
  HardDrive,
  User,
  Tag,
  Loader2,
  AlertCircle,
  ExternalLink,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { toast } from "sonner";

export default function DocumentDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const resolvedParams = use(params);
  const docId = resolvedParams.id;

  const { t } = useTranslation();
  const { user } = useAuth();
  const router = useRouter();

  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [team, setTeam] = useState<TeamResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [copiedText, setCopiedText] = useState(false);
  const [copiedSummary, setCopiedSummary] = useState(false);

  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  const fetchDocument = useCallback(
    async (showLoading = true) => {
      if (showLoading) setLoading(true);
      try {
        const doc = await documentsApi.getDocumentById(docId);
        setDocument(doc);

        if (doc.team_id) {
          teamsApi
            .getTeamById(doc.team_id)
            .then(setTeam)
            .catch(() => {});
        }
      } catch {
        // If not found or error, handled by api client
      } finally {
        if (showLoading) setLoading(false);
      }
    },
    [docId]
  );

  useEffect(() => {
    fetchDocument(true);
  }, [fetchDocument]);

  // Auto-polling if document is still processing
  useEffect(() => {
    if (
      document &&
      (document.status === "PENDING" ||
        document.status === "OCR_IN_PROGRESS" ||
        document.status === "GENAI_IN_PROGRESS")
    ) {
      pollTimerRef.current = setTimeout(() => {
        fetchDocument(false);
      }, 3000);
    }

    return () => {
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, [document, fetchDocument]);

  const handleDelete = async () => {
    if (!document) return;
    setDeleteLoading(true);
    try {
      await documentsApi.deleteDocument(document.id);
      toast.success(t.documents.deleteSuccess);
      router.push("/dashboard");
    } catch {
      // Handled by api client
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleCopy = (text: string, type: "ocr" | "summary") => {
    navigator.clipboard.writeText(text);
    if (type === "ocr") {
      setCopiedText(true);
      setTimeout(() => setCopiedText(false), 2000);
    } else {
      setCopiedSummary(true);
      setTimeout(() => setCopiedSummary(false), 2000);
    }
    toast.success(t.common.copied);
  };

  const formatFileSize = (bytes: number) => {
    if (!bytes || bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  };

  const formatDate = (isoString?: string) => {
    if (!isoString) return "-";
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

  const downloadUrl = documentsApi.getDownloadUrl(docId);

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        {/* Back Link & Top Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button
              variant="outline"
              size="icon"
              onClick={() => router.push("/dashboard")}
              className="h-9 w-9 border-slate-200 text-slate-600 hover:text-slate-900"
            >
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900 truncate max-w-lg">
                  {document?.title || t.common.loading}
                </h1>
                {document && <StatusBadge status={document.status} />}
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                {document?.original_filename}
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          {document && (
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => window.open(downloadUrl, "_blank")}
                className="gap-1.5"
              >
                <Download className="h-4 w-4" />
                <span>{t.common.download}</span>
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setEditDialogOpen(true)}
                className="gap-1.5"
              >
                <Edit className="h-4 w-4" />
                <span>{t.common.edit}</span>
              </Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={() => setDeleteConfirmOpen(true)}
                className="gap-1.5 bg-red-600 hover:bg-red-700 text-white"
              >
                <Trash2 className="h-4 w-4" />
                <span>{t.common.delete}</span>
              </Button>
            </div>
          )}
        </div>

        {loading ? (
          <div className="flex flex-col items-center justify-center p-16 bg-white rounded-xl border border-slate-200">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-2" />
            <p className="text-sm text-slate-500">{t.common.loading}</p>
          </div>
        ) : !document ? (
          <div className="flex flex-col items-center justify-center p-16 bg-white rounded-xl border border-slate-200 text-center">
            <AlertCircle className="h-10 w-10 text-red-500 mb-3" />
            <h3 className="text-base font-semibold text-slate-900">
              {t.errors.notFound}
            </h3>
            <p className="text-sm text-slate-500 mt-1 mb-4">
              This document does not exist or you do not have permission to view it.
            </p>
            <Button
              onClick={() => router.push("/dashboard")}
              className="bg-blue-600 hover:bg-blue-700 text-white"
            >
              {t.common.back}
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left 2 Cols: PDF Preview & Extracted Text / AI Summary Tabs */}
            <div className="lg:col-span-2 space-y-6">
              {/* PDF Previewer */}
              <Card className="border-slate-200 bg-white overflow-hidden shadow-xs">
                <CardHeader className="py-3 px-4 border-b border-slate-100 flex flex-row items-center justify-between">
                  <CardTitle className="text-sm font-semibold flex items-center gap-2">
                    <FileText className="h-4 w-4 text-blue-600" />
                    {t.documents.previewPdf}
                  </CardTitle>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-xs h-7 gap-1 text-slate-500 hover:text-slate-900"
                    onClick={() => window.open(downloadUrl, "_blank")}
                  >
                    <ExternalLink className="h-3.5 w-3.5" />
                    {t.common.details}
                  </Button>
                </CardHeader>
                <div className="h-[480px] bg-slate-100 relative">
                  <iframe
                    src={`${downloadUrl}#toolbar=0`}
                    className="w-full h-full border-none"
                    title={document.title}
                  />
                </div>
              </Card>

              {/* Tabs for AI Summary & Extracted Text (OCR) */}
              <Card className="border-slate-200 bg-white shadow-xs">
                <CardContent className="p-6">
                  <Tabs defaultValue="summary" className="w-full">
                    <TabsList className="grid w-full grid-cols-2 mb-4">
                      <TabsTrigger value="summary" className="gap-2">
                        <Sparkles className="h-4 w-4 text-purple-600" />
                        <span>{t.documents.aiSummary}</span>
                      </TabsTrigger>
                      <TabsTrigger value="ocr" className="gap-2">
                        <AlignLeft className="h-4 w-4 text-blue-600" />
                        <span>{t.documents.ocrContent}</span>
                      </TabsTrigger>
                    </TabsList>

                    {/* AI SUMMARY TAB */}
                    <TabsContent value="summary" className="space-y-3">
                      <div className="flex items-center justify-between">
                        <h4 className="text-sm font-semibold text-slate-900">
                          {t.documents.aiSummary}
                        </h4>
                        {document.summary && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() =>
                              handleCopy(document.summary || "", "summary")
                            }
                            className="h-8 gap-1.5 text-xs"
                          >
                            {copiedSummary ? (
                              <>
                                <Check className="h-3.5 w-3.5 text-emerald-600" />
                                <span>{t.common.copied}</span>
                              </>
                            ) : (
                              <>
                                <Copy className="h-3.5 w-3.5" />
                                <span>{t.common.copy}</span>
                              </>
                            )}
                          </Button>
                        )}
                      </div>

                      {document.summary ? (
                        <div className="p-4 bg-purple-50/50 rounded-lg border border-purple-100 text-sm text-slate-800 leading-relaxed whitespace-pre-wrap">
                          {document.summary}
                        </div>
                      ) : document.status === "GENAI_IN_PROGRESS" ||
                        document.status === "OCR_IN_PROGRESS" ||
                        document.status === "PENDING" ? (
                        <div className="flex items-center gap-3 p-6 bg-slate-50 rounded-lg border border-slate-200 text-slate-500 text-sm">
                          <Loader2 className="h-5 w-5 animate-spin text-purple-600 shrink-0" />
                          <span>
                            AI summary is being generated by Google Gemini. Please wait...
                          </span>
                        </div>
                      ) : (
                        <p className="text-sm text-slate-400 italic p-4 bg-slate-50 rounded-lg border border-slate-200">
                          {t.documents.aiSummaryEmpty}
                        </p>
                      )}
                    </TabsContent>

                    {/* OCR EXTRACTED TEXT TAB */}
                    <TabsContent value="ocr" className="space-y-3">
                      <div className="flex items-center justify-between">
                        <h4 className="text-sm font-semibold text-slate-900">
                          {t.documents.ocrContent}
                        </h4>
                        {document.ocr_content && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() =>
                              handleCopy(document.ocr_content || "", "ocr")
                            }
                            className="h-8 gap-1.5 text-xs"
                          >
                            {copiedText ? (
                              <>
                                <Check className="h-3.5 w-3.5 text-emerald-600" />
                                <span>{t.common.copied}</span>
                              </>
                            ) : (
                              <>
                                <Copy className="h-3.5 w-3.5" />
                                <span>{t.common.copy}</span>
                              </>
                            )}
                          </Button>
                        )}
                      </div>

                      {document.ocr_content ? (
                        <div className="p-4 bg-slate-50 rounded-lg border border-slate-200 font-mono text-xs text-slate-800 max-h-96 overflow-y-auto whitespace-pre-wrap leading-relaxed">
                          {document.ocr_content}
                        </div>
                      ) : document.status === "OCR_IN_PROGRESS" ||
                        document.status === "PENDING" ? (
                        <div className="flex items-center gap-3 p-6 bg-slate-50 rounded-lg border border-slate-200 text-slate-500 text-sm">
                          <Loader2 className="h-5 w-5 animate-spin text-blue-600 shrink-0" />
                          <span>
                            OCR text extraction is running. Extracted text will appear automatically.
                          </span>
                        </div>
                      ) : (
                        <p className="text-sm text-slate-400 italic p-4 bg-slate-50 rounded-lg border border-slate-200">
                          {t.documents.ocrEmpty}
                        </p>
                      )}
                    </TabsContent>
                  </Tabs>
                </CardContent>
              </Card>
            </div>

            {/* Right Sidebar: Metadata */}
            <div className="space-y-6">
              <Card className="border-slate-200 bg-white shadow-xs">
                <CardHeader className="pb-3 border-b border-slate-100">
                  <CardTitle className="text-base font-semibold">
                    {t.common.details}
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-4 divide-y divide-slate-100 text-sm">
                  {/* Status */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500">{t.common.status}</span>
                    <StatusBadge status={document.status} />
                  </div>

                  {/* Classification */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500 flex items-center gap-1.5">
                      <Tag className="h-3.5 w-3.5 text-slate-400" />
                      {t.documents.docType}
                    </span>
                    {document.document_type ? (
                      <Badge variant="outline" className="font-normal text-xs">
                        {document.document_type.name}
                      </Badge>
                    ) : (
                      <span className="text-xs text-slate-400">
                        {t.documents.noDocType}
                      </span>
                    )}
                  </div>

                  {/* Sharing */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500 flex items-center gap-1.5">
                      {team ? (
                        <Users className="h-3.5 w-3.5 text-blue-500" />
                      ) : (
                        <Lock className="h-3.5 w-3.5 text-slate-400" />
                      )}
                      {t.documents.teamSharing}
                    </span>
                    {team ? (
                      <Link
                        href={`/teams/${team.id}`}
                        className="text-xs font-medium text-blue-600 hover:underline"
                      >
                        {team.name}
                      </Link>
                    ) : (
                      <span className="text-xs text-slate-600">
                        {t.common.private}
                      </span>
                    )}
                  </div>

                  {/* File Size */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500 flex items-center gap-1.5">
                      <HardDrive className="h-3.5 w-3.5 text-slate-400" />
                      {t.documents.fileSize}
                    </span>
                    <span className="text-xs font-medium text-slate-700">
                      {formatFileSize(document.file_size)}
                    </span>
                  </div>

                  {/* Upload Date */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500 flex items-center gap-1.5">
                      <Calendar className="h-3.5 w-3.5 text-slate-400" />
                      {t.documents.createdAt}
                    </span>
                    <span className="text-xs text-slate-700">
                      {formatDate(document.created_at)}
                    </span>
                  </div>

                  {/* Last Updated */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500 flex items-center gap-1.5">
                      <Calendar className="h-3.5 w-3.5 text-slate-400" />
                      {t.documents.updatedAt}
                    </span>
                    <span className="text-xs text-slate-700">
                      {formatDate(document.updated_at)}
                    </span>
                  </div>

                  {/* Uploader / Owner */}
                  <div className="py-2.5 flex items-center justify-between">
                    <span className="text-xs text-slate-500 flex items-center gap-1.5">
                      <User className="h-3.5 w-3.5 text-slate-400" />
                      {t.documents.uploadedBy}
                    </span>
                    <span className="text-xs text-slate-700 font-mono">
                      {user?.id === document.owner_id ? "You" : document.owner_id.slice(0, 8)}
                    </span>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {/* Edit Dialog */}
        <EditDocumentDialog
          document={document}
          open={editDialogOpen}
          onOpenChange={setEditDialogOpen}
          onSuccess={() => fetchDocument(false)}
        />

        {/* Delete Confirm Dialog */}
        <ConfirmDialog
          open={deleteConfirmOpen}
          onOpenChange={setDeleteConfirmOpen}
          title={t.documents.deleteConfirmTitle}
          description={t.documents.deleteConfirmText}
          confirmLabel={t.common.delete}
          loading={deleteLoading}
          onConfirm={handleDelete}
        />
      </div>
    </ProtectedRoute>
  );
}
