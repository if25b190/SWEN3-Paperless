import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  DialogActions,
  Stack,
  TextField,
} from "@mui/material";
import { apiUpload, type Document } from "../../lib/api";
import { Modal } from "../shared/Modal";

export function UploadDialog({
  onClose,
  onUploaded,
}: {
  onClose: () => void;
  onUploaded: (d: Document) => void;
}) {
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file || !title) return;
    setError("");
    setLoading(true);
    try {
      onUploaded(
        await apiUpload<Document>("/documents", { document: file, title }),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal title="Upload document" onClose={onClose}>
      <Box component="form" onSubmit={submit}>
        <Stack sx={{ gap: 2, mt: 1 }}>
          <TextField
            label="Title"
            required
            autoFocus
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <TextField
            label="File"
            type="file"
            required
            slotProps={{ inputLabel: { shrink: true } }}
            onChange={(e) => setFile((e.target as HTMLInputElement).files?.[0])}
          />
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
        </Stack>
        <DialogActions sx={{ px: 0, mt: 3 }}>
          <Button type="button" variant="outlined" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? "Uploading…" : "Upload document"}
          </Button>
        </DialogActions>
      </Box>
    </Modal>
  );
}
