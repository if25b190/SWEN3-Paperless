"use client";

import { useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  DialogActions,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { apiUpload, ApiError, invalidParamFor, type Document, type DocumentType, type Team } from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { useToast } from "../../lib/toast/ToastProvider";
import { bytes } from "./document-utils";
import { Modal } from "../shared/Modal";

export function UploadDialog({
  onClose,
  onUploaded,
  types,
  writableTeams,
}: {
  onClose: () => void;
  onUploaded: (d: Document) => void;
  types: DocumentType[];
  writableTeams: Team[];
}) {
  const { t } = useI18n();
  const { toast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [typeId, setTypeId] = useState("");
  const [teamId, setTeamId] = useState("");
  const [error, setError] = useState("");
  const [titleError, setTitleError] = useState("");
  const [fileError, setFileError] = useState("");
  const [dragOver, setDragOver] = useState(false);
  const [loading, setLoading] = useState(false);

  const acceptFile = (candidate: File) => {
    setError("");
    setFileError("");
    if (candidate.type === "application/pdf" || candidate.name.toLowerCase().endsWith(".pdf")) {
      setFile(candidate);
    } else {
      setFile(null);
      setFileError(t("upload.pdf_only"));
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setTitleError("");
    setFileError("");
    if (!title.trim()) {
      setTitleError(t("validation.required"));
      return;
    }
    if (!file) {
      setFileError(t("validation.required"));
      return;
    }
    setLoading(true);
    try {
      const values: Record<string, string | File | null> = { document: file, title: title.trim() };
      if (typeId) values.document_type_id = typeId;
      if (teamId) values.team_id = teamId;
      const doc = await apiUpload<Document>("/documents", values);
      toast("success", t("messages.upload_success"));
      onUploaded(doc);
    } catch (err) {
      if (err instanceof ApiError) {
        const titleParam = invalidParamFor(err, "title");
        if (titleParam) {
          setTitleError(titleParam);
          setLoading(false);
          return;
        }
      }
      setError(err instanceof Error ? err.message : t("upload.error"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal title={t("upload.title")} onClose={onClose}>
      <Box component="form" onSubmit={submit} noValidate>
        <Stack sx={{ gap: 2, mt: 1 }}>
          <TextField
            label={t("upload.field_title")}
            required
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            error={Boolean(titleError)}
            helperText={titleError}
          />
          <Box
            role="button"
            tabIndex={0}
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") fileInputRef.current?.click(); }}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              const dropped = e.dataTransfer.files?.[0];
              if (dropped) acceptFile(dropped);
            }}
            sx={{
              border: "2px dashed",
              borderColor: fileError ? "error.main" : dragOver ? "primary.main" : "divider",
              borderRadius: 3,
              p: 4,
              textAlign: "center",
              cursor: "pointer",
              bgcolor: dragOver ? "action.hover" : "transparent",
              transition: "background-color 150ms, border-color 150ms",
            }}
          >
            {file ? (
              <Typography>{file.name} ({bytes(file.size)})</Typography>
            ) : (
              <Typography color="text.secondary">{t("upload.drop")}</Typography>
            )}
            <Button
              component="span"
              variant="outlined"
              sx={{ mt: 1.5, display: "inline-flex" }}
              onClick={(e) => { e.stopPropagation(); fileInputRef.current?.click(); }}
            >
              {t("upload.browse")}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf,.pdf"
              hidden
              onChange={(e) => {
                const chosen = (e.target as HTMLInputElement).files?.[0];
                if (chosen) acceptFile(chosen);
              }}
            />
          </Box>
          {fileError && <Alert severity="error">{fileError}</Alert>}
          <TextField select label={t("upload.document_type")} value={typeId} onChange={(e) => setTypeId(e.target.value)}>
            <MenuItem value="">{t("upload.no_type")}</MenuItem>
            {types.map((type) => (
              <MenuItem key={type.id} value={type.id}>{type.name}</MenuItem>
            ))}
          </TextField>
          <TextField select label={t("upload.team")} value={teamId} onChange={(e) => setTeamId(e.target.value)}>
            <MenuItem value="">{t("upload.keep_private")}</MenuItem>
            {writableTeams.map((team) => (
              <MenuItem key={team.id} value={team.id}>{team.name}</MenuItem>
            ))}
          </TextField>
          {error && <Alert severity="error" role="alert">{error}</Alert>}
        </Stack>
        <DialogActions sx={{ px: 0, mt: 3 }}>
          <Button type="button" variant="outlined" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? t("upload.uploading") : t("upload.title")}
          </Button>
        </DialogActions>
      </Box>
    </Modal>
  );
}
