import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  DialogActions,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import DeleteOutlineRounded from "@mui/icons-material/DeleteOutlineRounded";
import DownloadRounded from "@mui/icons-material/DownloadRounded";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";
import {
  api,
  apiGetFile,
  apiGetFileBlob,
  apiUpdateDocument,
  type Correspondent,
  type Document,
  type DocumentType,
} from "../../lib/api";
import { Modal } from "../shared/Modal";
import { bytes, previewMime, type PreviewState } from "./document-utils";

export function DocumentDetail({
  document,
  correspondents,
  types,
  onClose,
  onSaved,
  onDelete,
}: {
  document: Document;
  correspondents: Correspondent[];
  types: DocumentType[];
  onClose: () => void;
  onSaved: (d: Document) => void;
  onDelete: (id: number) => void;
}) {
  const [title, setTitle] = useState(document.title);
  const [correspondentId, setCorrespondentId] = useState(
    String(document.correspondent?.id || ""),
  );
  const [typeId, setTypeId] = useState(
    String(document.document_type?.id || ""),
  );
  const [error, setError] = useState("");
  const [preview, setPreview] = useState<PreviewState | null>(null);
  const requestId = useRef(0);
  const previewUrl = useRef<string | null>(null);
  const safeMime = previewMime(document);
  const previewKey = `${document.id}:${document.original_filename}:${document.content_type}`;
  useEffect(
    () => () => {
      requestId.current++;
      if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
      previewUrl.current = null;
    },
    [previewKey],
  );
  const loadPreview = async () => {
    if (!safeMime) return;
    const request = ++requestId.current;
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = null;
    setPreview({ key: previewKey, stage: "loading" });
    try {
      const blob = await apiGetFileBlob(document.id);
      if (request !== requestId.current) return;
      if (safeMime === "text/plain") {
        const text = await blob.text();
        if (request === requestId.current)
          setPreview({ key: previewKey, stage: "ready", text });
      } else {
        // The download response may be generic; only use the allowlisted metadata type.
        const url = URL.createObjectURL(new Blob([blob], { type: safeMime }));
        if (request !== requestId.current) URL.revokeObjectURL(url);
        else {
          previewUrl.current = url;
          setPreview({ key: previewKey, stage: "ready", url });
        }
      }
    } catch (error) {
      if (request === requestId.current)
        setPreview({
          key: previewKey,
          stage: "error",
          message:
            error instanceof Error
              ? error.message
              : "Preview could not be loaded.",
        });
    }
  };
  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      await Promise.all([
        correspondentId
          ? api.correspondent(Number(correspondentId))
          : Promise.resolve(),
        typeId ? api.documentType(Number(typeId)) : Promise.resolve(),
      ]);
      onSaved(
        await apiUpdateDocument(document.id, {
          title,
          correspondent_id: correspondentId ? Number(correspondentId) : null,
          document_type_id: typeId ? Number(typeId) : null,
        }),
      );
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Document could not be updated.",
      );
    }
  };
  const visiblePreview = preview?.key === previewKey ? preview : null;
  return (
    <Modal title="Document details" onClose={onClose}>
      {error && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Typography color="text.secondary" variant="body2" sx={{ mb: 2 }}>
        {document.original_filename} · {bytes(document.file_size)}
      </Typography>
      <Stack direction="row" sx={{ gap: 1, flexWrap: "wrap", mb: 2.5 }}>
        {safeMime && (
          <Button
            type="button"
            variant="contained"
            color="secondary"
            startIcon={<VisibilityOutlined />}
            disabled={visiblePreview?.stage === "loading"}
            onClick={() => void loadPreview()}
          >
            Preview
          </Button>
        )}
        <Button
          type="button"
          variant="outlined"
          startIcon={<DownloadRounded fontSize="small" />}
          onClick={() =>
            apiGetFile(document.id, document.original_filename).catch((e) =>
              setError(e instanceof Error ? e.message : "Download failed."),
            )
          }
        >
          Download
        </Button>
      </Stack>
      {!safeMime && (
        <Alert severity="info" sx={{ mb: 3 }}>
          Preview is unavailable for this file type. Download it to open it.
        </Alert>
      )}
      {visiblePreview && (
        <Paper
          variant="outlined"
          sx={{ p: 2, mb: 3, bgcolor: "background.default", minWidth: 0 }}
        >
          {visiblePreview.stage === "loading" && (
            <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
              <CircularProgress size={20} aria-label="Loading preview" />
              <Typography variant="body2">Loading preview…</Typography>
            </Stack>
          )}
          {visiblePreview.stage === "error" && (
            <Alert severity="error">{visiblePreview.message}</Alert>
          )}
          {visiblePreview.stage === "ready" && safeMime === "text/plain" && (
            <Typography
              component="pre"
              variant="body2"
              sx={{
                m: 0,
                whiteSpace: "pre-wrap",
                overflowWrap: "anywhere",
                maxHeight: 440,
                overflow: "auto",
              }}
            >
              {visiblePreview.text}
            </Typography>
          )}
          {visiblePreview.stage === "ready" &&
            safeMime?.startsWith("image/") && (
              <Box
                component="img"
                src={visiblePreview.url}
                alt={`Preview of ${document.title}`}
                sx={{
                  display: "block",
                  maxWidth: "100%",
                  maxHeight: 440,
                  objectFit: "contain",
                  mx: "auto",
                }}
              />
            )}
          {visiblePreview.stage === "ready" &&
            safeMime === "application/pdf" && (
              <Box
                component="iframe"
                title={`Preview of ${document.title}`}
                src={visiblePreview.url}
                sandbox="allow-scripts"
                sx={{
                  display: "block",
                  border: 0,
                  width: "100%",
                  height: 440,
                  bgcolor: "background.paper",
                }}
              />
            )}
        </Paper>
      )}
      <Box component="form" onSubmit={save}>
        <Stack sx={{ gap: 2 }}>
          <TextField
            label="Title"
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <FormControl size="small" fullWidth>
            <InputLabel id="document-correspondent-label" shrink>
              Document correspondent
            </InputLabel>
            <Select
              labelId="document-correspondent-label"
              label="Document correspondent"
              displayEmpty
              value={correspondentId}
              onChange={(e) => setCorrespondentId(e.target.value)}
            >
              <MenuItem value="">None</MenuItem>
              {correspondents.map((x) => (
                <MenuItem key={x.id} value={String(x.id)}>
                  {x.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" fullWidth>
            <InputLabel id="document-type-label" shrink>
              Document type
            </InputLabel>
            <Select
              labelId="document-type-label"
              label="Document type"
              displayEmpty
              value={typeId}
              onChange={(e) => setTypeId(e.target.value)}
            >
              <MenuItem value="">None</MenuItem>
              {types.map((x) => (
                <MenuItem key={x.id} value={String(x.id)}>
                  {x.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
        <DialogActions sx={{ px: 0, mt: 3, flexWrap: "wrap" }}>
          <Button
            type="button"
            variant="outlined"
            color="error"
            startIcon={<DeleteOutlineRounded fontSize="small" />}
            onClick={() => onDelete(document.id)}
            sx={{ mr: "auto" }}
          >
            Delete
          </Button>
          <Button type="submit" variant="contained">
            Save changes
          </Button>
        </DialogActions>
      </Box>
    </Modal>
  );
}
