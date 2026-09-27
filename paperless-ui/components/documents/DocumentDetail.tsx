"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import AutoAwesomeOutlined from "@mui/icons-material/AutoAwesomeOutlined";
import ArticleOutlined from "@mui/icons-material/ArticleOutlined";
import ContentCopyRounded from "@mui/icons-material/ContentCopyRounded";
import DeleteOutlineRounded from "@mui/icons-material/DeleteOutlineRounded";
import DownloadRounded from "@mui/icons-material/DownloadRounded";
import TextSnippetOutlined from "@mui/icons-material/TextSnippetOutlined";
import {
  ApiError,
  api,
  apiGetFile,
  apiGetFileBlob,
  apiUpdateDocument,
  type Document,
  type DocumentType,
  type Team,
  type UpdateDocumentBody,
  type User,
} from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { useToast } from "../../lib/toast/ToastProvider";
import { Modal } from "../shared/Modal";
import { bytes, statusTone } from "./document-utils";

const safePreviewMime = (mime: string): string | null => {
  if (mime.startsWith("image/") && !mime.includes("svg")) return mime;
  if (mime === "application/pdf") return "application/pdf";
  if (["text/plain", "text/markdown"].includes(mime)) return mime;
  return null;
};

const previewMime = (document: Document) =>
  safePreviewMime(document.content_type) ??
  (document.original_filename.toLowerCase().endsWith(".txt")
    ? "text/plain"
    : null);

type PreviewState =
  | { state: "loading" }
  | { state: "ready"; src: string; mime: string; text?: string }
  | { state: "error" }
  | { state: "unavailable" };

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" sx={{ justifyContent: "space-between", gap: 2 }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
        {value}
      </Typography>
    </Stack>
  );
}

export function DocumentDetail({
  document,
  user,
  types,
  teams,
  writableTeams,
  onClose,
  onSaved,
  onDelete,
}: {
  document: Document;
  user: User | null;
  types: DocumentType[];
  teams: Team[];
  writableTeams: Team[];
  onClose: () => void;
  onSaved: (doc: Document) => void;
  onDelete: (id: string) => void;
}) {
  const { t, locale } = useI18n();
  const { toast } = useToast();
  const [title, setTitle] = useState(document.title);
  const [typeId, setTypeId] = useState(document.document_type?.id ?? "");
  const [teamId, setTeamId] = useState(document.team_id ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fetchedOwnerName, setFetchedOwnerName] = useState<string | null>(null);
  const [preview, setPreview] = useState<PreviewState | null>(() =>
    previewMime(document) ? { state: "loading" } : { state: "unavailable" },
  );
  const previewRequest = useRef(0);
  const previewUrl = useRef<string | null>(null);
  const ownerRequest = useRef(0);

  const isOwner = user?.id === document.owner_id;
  const teamName = teams.find((team) => team.id === document.team_id)?.name;

  const shareTeams = useMemo(() => {
    const options = [...writableTeams];
    const current = teams.find((team) => team.id === document.team_id);
    if (current && !options.some((team) => team.id === current.id)) options.unshift(current);
    return options;
  }, [writableTeams, teams, document.team_id]);

  const previewKey = `${document.id}:${document.original_filename}:${document.content_type}`;
  useEffect(
    () => () => {
      previewRequest.current++;
      if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
      previewUrl.current = null;
    },
    [previewKey],
  );
  useEffect(() => {
    const mime = previewMime(document);
    if (!mime) return;
    const request = ++previewRequest.current;
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = null;
    void (async () => {
      try {
        const blob = await apiGetFileBlob(document.id);
        if (previewRequest.current !== request) return;
        const src = URL.createObjectURL(blob);
        if (mime.startsWith("image/") || mime === "application/pdf") {
          previewUrl.current = src;
          setPreview({ state: "ready", src, mime });
          return;
        }
        const text = await blob.text();
        if (previewRequest.current !== request) {
          if (previewUrl.current === src) previewUrl.current = null;
          URL.revokeObjectURL(src);
          return;
        }
        previewUrl.current = src;
        setPreview({ state: "ready", src, mime, text });
      } catch {
        if (previewRequest.current === request) setPreview({ state: "error" });
      }
    })();
  }, [document]);

  useEffect(() => {
    if (isOwner) return;
    const request = ++ownerRequest.current;
    api
      .user(document.owner_id)
      .then((u) => {
        if (ownerRequest.current === request) setFetchedOwnerName(u.username);
      })
      .catch(() => undefined);
  }, [isOwner, document.owner_id]);

  const ownerName = isOwner ? (user?.username ?? null) : fetchedOwnerName;
  const visiblePreview = preview?.state === "ready" ? preview : null;

  const copyOcr = async () => {
    if (!document.ocr_content) return;
    try {
      await navigator.clipboard.writeText(document.ocr_content);
    } catch {
      /* clipboard unavailable */
    }
  };

  const downloadDocument = async () => {
    try {
      await apiGetFile(document.id, document.original_filename);
    } catch {
      toast("error", t("detail.download_failed"));
    }
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const body: UpdateDocumentBody = { title, document_type_id: typeId || null };
      if (isOwner) {
        body.team_id = teamId || null;
        body.clear_team = !teamId;
      }
      const updated = await apiUpdateDocument(document.id, body);
      onSaved(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.update_failed"));
    } finally {
      setSaving(false);
    }
  };

  const createdDate = new Date(document.created_at).toLocaleDateString(
    locale === "de" ? "de-DE" : "en-GB",
    { day: "2-digit", month: "short", year: "numeric" },
  );

  return (
    <Modal title={t("detail.title")} onClose={onClose} maxWidth="md">
      <Box
        sx={{
          display: "grid",
          gap: 3,
          gridTemplateColumns: { xs: "1fr", sm: "minmax(0, 1.35fr) minmax(250px, 1fr)" },
        }}
      >
        <Stack spacing={2.5} sx={{ minWidth: 0 }}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={1.5}
            sx={{ alignItems: { xs: "flex-start", sm: "center" }, justifyContent: "space-between", gap: 1 }}
          >
            <Stack spacing={0.25} sx={{ minWidth: 0 }}>
              <Typography sx={{ fontWeight: 700, fontSize: 17 }} noWrap>
                {document.title}
              </Typography>
              <Typography variant="body2" color="text.secondary" noWrap>
                {document.original_filename} · {bytes(document.file_size)}
              </Typography>
            </Stack>
            <Chip
              label={t(`status.${document.status}`)}
              color={statusTone[document.status]}
              size="small"
              sx={{ borderRadius: 1, fontWeight: 600 }}
            />
          </Stack>
          <Paper variant="outlined" sx={{ borderRadius: 2.5, p: 1.5, minHeight: 320 }}>
            {visiblePreview ? (
              visiblePreview.mime.startsWith("image/") ? (
                <Box
                  component="img"
                  src={visiblePreview.src}
                  alt={document.title}
                  sx={{ width: "100%", borderRadius: 12, display: "block" }}
                />
              ) : visiblePreview.mime === "application/pdf" ? (
                <iframe
                  src={visiblePreview.src}
                  title={document.title}
                  sandbox="allow-scripts"
                  style={{ width: "100%", height: 520, border: 0, borderRadius: 12, display: "block" }}
                />
              ) : (
                <Box
                  component="pre"
                  style={{
                    margin: 0,
                    fontSize: 12.5,
                    lineHeight: 1.55,
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                    maxHeight: 420,
                    overflow: "auto",
                  }}
                >
                  {visiblePreview.text}
                </Box>
              )
            ) : (
              <Box
                sx={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: 1.5,
                  p: 3,
                  color: "text.secondary",
                }}
              >
                <ArticleOutlined sx={{ fontSize: 40, opacity: 0.5 }} />
                <Typography variant="body2" sx={{ maxWidth: 320, textAlign: "center" }}>
                  {t("detail.preview_unavailable")}
                </Typography>
              </Box>
            )}
            {preview?.state === "loading" && (
              <Typography variant="body2" sx={{ mt: 1.5 }} color="text.secondary">
                {t("detail.loading_preview")}
              </Typography>
            )}
            {preview?.state === "error" && (
              <Alert severity="error" sx={{ mt: 1.5 }}>
                {t("detail.download_failed")}
              </Alert>
            )}
            <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
              <Button
                variant="outlined"
                size="small"
                startIcon={<DownloadRounded />}
                onClick={() => void downloadDocument()}
              >
                {t("detail.download")}
              </Button>
            </Stack>
          </Paper>
          <Paper variant="outlined" sx={{ borderRadius: 2.5, p: 2 }}>
            <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: "center" }}>
              <AutoAwesomeOutlined sx={{ fontSize: 18, color: "primary.main" }} />
              <Typography sx={{ fontWeight: 650 }}>{t("detail.ai_summary")}</Typography>
            </Stack>
            {document.summary ? (
              <Typography variant="body2" sx={{ color: "text.secondary", whiteSpace: "pre-wrap", lineHeight: 1.6 }}>
                {document.summary}
              </Typography>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t("detail.ai_summary_empty")}
              </Typography>
            )}
          </Paper>
          <Paper variant="outlined" sx={{ borderRadius: 2.5, p: 2 }}>
            <Stack direction="row" sx={{ mb: 1, alignItems: "center", justifyContent: "space-between" }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                <TextSnippetOutlined sx={{ fontSize: 18, color: "primary.main" }} />
                <Typography sx={{ fontWeight: 650 }}>{t("detail.ocr")}</Typography>
              </Stack>
              {document.ocr_content && (
                <Button size="small" startIcon={<ContentCopyRounded />} onClick={() => void copyOcr()}>
                  {t("detail.copy")}
                </Button>
              )}
            </Stack>
            {document.ocr_content ? (
              <Box
                component="pre"
                style={{
                  margin: 0,
                  fontSize: 12,
                  lineHeight: 1.55,
                  whiteSpace: "pre-wrap",
                  wordBreak: "break-word",
                  maxHeight: 260,
                  overflow: "auto",
                  color: "text.secondary",
                }}
              >
                {document.ocr_content}
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t("detail.ocr_empty")}
              </Typography>
            )}
          </Paper>
        </Stack>
        <Stack spacing={2.5} sx={{ minWidth: 0 }}>
          <Paper variant="outlined" sx={{ borderRadius: 2.5, p: 2 }}>
            <Typography variant="overline" sx={{ color: "text.secondary" }}>
              {t("detail.metadata")}
            </Typography>
            <Stack spacing={1.5} sx={{ mt: 1 }}>
              <MetaRow label={t("detail.owner")} value={ownerName ?? t("detail.owner_unknown")} />
              <MetaRow label={t("detail.team")} value={teamName ?? t("detail.private")} />
              <MetaRow label={t("detail.created")} value={createdDate} />
              <MetaRow label={t("detail.size")} value={bytes(document.file_size)} />
            </Stack>
          </Paper>
          <Paper variant="outlined" sx={{ borderRadius: 2.5, p: 2 }}>
            <Typography variant="overline" sx={{ color: "text.secondary" }}>
              {t("detail.edit")}
            </Typography>
            <Stack
              component="form"
              spacing={2}
              sx={{ mt: 1 }}
              onSubmit={(e) => {
                e.preventDefault();
                void save(e);
              }}
            >
              <TextField
                label={t("upload.field_title")}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                fullWidth
                size="small"
              />
              <TextField
                select
                label={t("upload.document_type")}
                value={typeId}
                onChange={(e) => setTypeId(e.target.value)}
                fullWidth
                size="small"
              >
                <MenuItem value="">{t("upload.no_type")}</MenuItem>
                {types.map((type) => (
                  <MenuItem key={type.id} value={type.id}>
                    {type.name}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label={t("detail.share_team")}
                value={teamId}
                onChange={(e) => setTeamId(e.target.value)}
                fullWidth
                size="small"
                disabled={!isOwner}
                helperText={!isOwner ? t("detail.owner_only_note") : undefined}
              >
                <MenuItem value="">{t("upload.keep_private")}</MenuItem>
                {shareTeams.map((team) => (
                  <MenuItem key={team.id} value={team.id}>
                    {team.name}
                  </MenuItem>
                ))}
              </TextField>
              {error && <Alert severity="error">{error}</Alert>}
              <Stack direction="row" spacing={1}>
                <Button type="submit" variant="contained" size="small" disabled={saving}>
                  {t("common.save")}
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  color="error"
                  startIcon={<DeleteOutlineRounded />}
                  onClick={() => onDelete(document.id)}
                >
                  {t("detail.delete")}
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </Stack>
      </Box>
    </Modal>
  );
}
