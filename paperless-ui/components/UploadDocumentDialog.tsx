"use client";

import React, { useState, useEffect, useRef } from "react";
import { useTranslation } from "@/lib/i18n/context";
import { useAuth } from "@/lib/auth/context";
import { documentsApi, documentTypesApi, usersApi, ApiError } from "@/lib/api/client";
import {
  DocumentTypeResponse,
  UserTeamMembershipResponse,
} from "@/lib/types/api";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "./ui/dialog";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "./ui/select";
import { UploadCloud, File, X, Loader2, AlertCircle } from "lucide-react";
import { toast } from "sonner";

interface UploadDocumentDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function UploadDocumentDialog({
  open,
  onOpenChange,
  onSuccess,
}: UploadDocumentDialogProps) {
  const { t } = useTranslation();
  const { user } = useAuth();

  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [documentTypeId, setDocumentTypeId] = useState<string>("none");
  const [teamId, setTeamId] = useState<string>("private");

  const [documentTypes, setDocumentTypes] = useState<DocumentTypeResponse[]>([]);
  const [writableTeams, setWritableTeams] = useState<UserTeamMembershipResponse[]>([]);

  const [isDragging, setIsDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) {
      // Reset form on close
      setFile(null);
      setTitle("");
      setDocumentTypeId("none");
      setTeamId("private");
      setErrorMessage(null);
      return;
    }

    // Load available document types
    documentTypesApi
      .getDocumentTypes()
      .then((res) => setDocumentTypes(res.items || []))
      .catch(() => {});

    // Load user's teams with READ_WRITE or ADMIN permissions
    if (user?.id) {
      usersApi
        .getUserTeams(user.id)
        .then((res) => {
          const writable = (res.items || []).filter(
            (m) => m.role === "ADMIN" || m.role === "READ_WRITE"
          );
          setWritableTeams(writable);
        })
        .catch(() => {});
    }
  }, [open, user?.id]);

  const handleFileChange = (selected: File | null) => {
    if (!selected) return;

    if (selected.type !== "application/pdf" && !selected.name.toLowerCase().endsWith(".pdf")) {
      setErrorMessage(t.documents.pdfOnly);
      return;
    }

    // 50MB limit check
    if (selected.size > 50 * 1024 * 1024) {
      setErrorMessage(t.documents.fileSizeLimit);
      return;
    }

    setErrorMessage(null);
    setFile(selected);

    // Auto-fill title from filename if title is empty
    if (!title.trim()) {
      const cleanName = selected.name.replace(/\.pdf$/i, "").replace(/[_-]/g, " ");
      setTitle(cleanName);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileChange(e.dataTransfer.files[0]);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!file) {
      setErrorMessage(t.errors.fileRequired);
      return;
    }

    if (!title.trim()) {
      setErrorMessage(t.errors.titleRequired);
      return;
    }

    setUploading(true);

    try {
      const formData = new FormData();
      formData.append("document", file);
      formData.append("title", title.trim());

      if (documentTypeId !== "none") {
        formData.append("document_type_id", documentTypeId);
      }

      if (teamId !== "private") {
        formData.append("team_id", teamId);
      }

      await documentsApi.uploadDocument(formData);
      toast.success(t.documents.uploadSuccess);
      onOpenChange(false);
      onSuccess?.();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.invalidParams.length > 0) {
          setErrorMessage(err.invalidParams.map((p) => p.reason).join(" "));
        } else {
          setErrorMessage(err.message);
        }
      } else {
        setErrorMessage("Upload failed. Please try again.");
      }
    } finally {
      setUploading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{t.documents.uploadTitle}</DialogTitle>
          <DialogDescription>{t.documents.uploadSubtitle}</DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-600" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Drag & Drop Area */}
          {!file ? (
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`flex flex-col items-center justify-center border-2 border-dashed rounded-lg p-6 cursor-pointer transition-colors ${
                isDragging
                  ? "border-blue-500 bg-blue-50"
                  : "border-slate-300 hover:border-slate-400 bg-slate-50"
              }`}
            >
              <UploadCloud className="h-10 w-10 text-slate-400 mb-2" />
              <p className="text-sm font-medium text-slate-700 text-center">
                {isDragging
                  ? t.documents.dragDropActive
                  : t.documents.dragDropText}
              </p>
              <p className="text-xs text-slate-400 mt-1">
                {t.documents.fileSizeLimit} (PDF)
              </p>
              <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf"
                className="hidden"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    handleFileChange(e.target.files[0]);
                  }
                }}
              />
            </div>
          ) : (
            <div className="flex items-center justify-between p-3 border border-slate-200 rounded-lg bg-slate-50">
              <div className="flex items-center gap-3 min-w-0">
                <div className="h-9 w-9 rounded-md bg-blue-100 text-blue-700 flex items-center justify-center shrink-0">
                  <File className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-900 truncate">
                    {file.name}
                  </p>
                  <p className="text-xs text-slate-500">
                    {(file.size / 1024 / 1024).toFixed(2)} MB
                  </p>
                </div>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => setFile(null)}
                className="text-slate-400 hover:text-slate-700 shrink-0"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          )}

          {/* Title Field */}
          <div className="space-y-1.5">
            <Label htmlFor="upload-title">{t.documents.docTitle} *</Label>
            <Input
              id="upload-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t.documents.docTitlePlaceholder}
              required
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Document Type Dropdown */}
            <div className="space-y-1.5">
              <Label>{t.documents.docType}</Label>
              <Select
                value={documentTypeId}
                onValueChange={setDocumentTypeId}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t.documents.noDocType} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">
                    {t.documents.noDocType}
                  </SelectItem>
                  {documentTypes.map((dt) => (
                    <SelectItem key={dt.id} value={dt.id}>
                      {dt.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Team Sharing Dropdown */}
            <div className="space-y-1.5">
              <Label>{t.documents.teamSharing}</Label>
              <Select value={teamId} onValueChange={setTeamId}>
                <SelectTrigger>
                  <SelectValue placeholder={t.documents.keepPrivate} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="private">
                    {t.documents.keepPrivate}
                  </SelectItem>
                  {writableTeams.map((membership) => (
                    <SelectItem
                      key={membership.team.id}
                      value={membership.team.id}
                    >
                      {membership.team.name} ({membership.role})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={uploading}
            >
              {t.common.cancel}
            </Button>
            <Button
              type="submit"
              disabled={uploading || !file || !title.trim()}
              className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
            >
              {uploading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t.documents.uploading}
                </>
              ) : (
                t.documents.uploadDocument
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
