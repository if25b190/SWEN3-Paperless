"use client";

import React, { useState, useEffect } from "react";
import { useTranslation } from "@/lib/i18n/context";
import { useAuth } from "@/lib/auth/context";
import { documentsApi, documentTypesApi, usersApi, ApiError } from "@/lib/api/client";
import {
  DocumentResponse,
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
import { Loader2, AlertCircle } from "lucide-react";
import { toast } from "sonner";

interface EditDocumentDialogProps {
  document: DocumentResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function EditDocumentDialog({
  document,
  open,
  onOpenChange,
  onSuccess,
}: EditDocumentDialogProps) {
  const { t } = useTranslation();
  const { user } = useAuth();

  const [title, setTitle] = useState("");
  const [documentTypeId, setDocumentTypeId] = useState<string>("none");
  const [sharingOption, setSharingOption] = useState<string>("keep");

  const [documentTypes, setDocumentTypes] = useState<DocumentTypeResponse[]>([]);
  const [writableTeams, setWritableTeams] = useState<UserTeamMembershipResponse[]>([]);

  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isOwner = user?.id && document?.owner_id === user.id;

  useEffect(() => {
    if (!open || !document) return;

    setTitle(document.title);
    setDocumentTypeId(document.document_type?.id || "none");
    setSharingOption("keep");
    setErrorMessage(null);

    documentTypesApi
      .getDocumentTypes()
      .then((res) => setDocumentTypes(res.items || []))
      .catch(() => {});

    if (user?.id && isOwner) {
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
  }, [open, document, user?.id, isOwner]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!document) return;
    setErrorMessage(null);

    if (!title.trim()) {
      setErrorMessage(t.errors.titleRequired);
      return;
    }

    setSubmitting(true);

    try {
      const payload: {
        title: string;
        document_type_id?: string | null;
        team_id?: string | null;
        clear_team?: boolean;
      } = {
        title: title.trim(),
        document_type_id: documentTypeId === "none" ? null : documentTypeId,
      };

      if (isOwner) {
        if (sharingOption === "private") {
          payload.clear_team = true;
        } else if (sharingOption !== "keep") {
          payload.team_id = sharingOption;
          payload.clear_team = false;
        }
      }

      await documentsApi.updateDocument(document.id, payload);
      toast.success(t.documents.editSuccess);
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
        setErrorMessage("Failed to update document.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t.documents.editDocument}</DialogTitle>
          <DialogDescription>
            Update title, classification, or team visibility.
          </DialogDescription>
        </DialogHeader>

        {errorMessage && (
          <div className="flex items-start gap-2.5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-600" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="edit-title">{t.documents.docTitle} *</Label>
            <Input
              id="edit-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

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

          {isOwner && (
            <div className="space-y-1.5">
              <Label>{t.documents.teamSharing}</Label>
              <Select
                value={sharingOption}
                onValueChange={setSharingOption}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Keep current sharing" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="keep">Keep current sharing</SelectItem>
                  <SelectItem value="private">
                    {t.documents.keepPrivate}
                  </SelectItem>
                  {writableTeams.map((membership) => (
                    <SelectItem
                      key={membership.team.id}
                      value={membership.team.id}
                    >
                      {membership.team.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={submitting}
            >
              {t.common.cancel}
            </Button>
            <Button
              type="submit"
              disabled={submitting || !title.trim()}
              className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
            >
              {submitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t.common.loading}
                </>
              ) : (
                t.common.save
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
