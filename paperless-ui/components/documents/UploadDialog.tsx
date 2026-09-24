import { useState } from "react";
import { Box, Button, DialogActions, Stack, TextField } from "@mui/material";
import { apiUpload, type Document } from "../../lib/api";
import { Modal } from "../shared/Modal";

export function UploadDialog({
  onClose,
  onUploaded,
  onError,
}: {
  onClose: () => void;
  onUploaded: (d: Document) => void;
  onError: (e: unknown, f: string) => void;
}) {
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File>();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file || !title) return;
    try {
      onUploaded(
        await apiUpload<Document>("/documents", { document: file, title }),
      );
    } catch (err) {
      onError(err, "Upload failed.");
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
        </Stack>
        <DialogActions sx={{ px: 0, mt: 3 }}>
          <Button type="submit" variant="contained">
            Upload document
          </Button>
        </DialogActions>
      </Box>
    </Modal>
  );
}
