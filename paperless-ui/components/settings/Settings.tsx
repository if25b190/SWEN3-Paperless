import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  DialogActions,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { api, type Correspondent, type DocumentType } from "../../lib/api";
import { Modal } from "../shared/Modal";
import { sectionSx } from "../shared/styles";

export function Settings({
  correspondents,
  types,
  onChange,
  onError,
}: {
  correspondents: Correspondent[];
  types: DocumentType[];
  onChange: (
    kind: "correspondents" | "document-types",
    items: (Correspondent | DocumentType)[],
  ) => void;
  onError: (e: unknown, f: string) => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [editingLabel, setEditingLabel] = useState<{
    kind: "correspondents" | "document-types";
    id: number;
    name: string;
    description: string;
    error?: string;
  } | null>(null);

  const add = async (kind: "correspondents" | "document-types") => {
    try {
      const item =
        kind === "correspondents"
          ? await api.createLabel(kind, { name, notes: description })
          : await api.createDocumentType({ name, description });
      onChange(kind, [
        ...(kind === "correspondents" ? correspondents : types),
        item,
      ]);
      setName("");
      setDescription("");
    } catch (err) {
      onError(err, "Item could not be created.");
    }
  };

  const row = (
    kind: "correspondents" | "document-types",
    item: Correspondent | DocumentType,
  ) => {
    const text =
      kind === "correspondents"
        ? (item as Correspondent).notes
        : (item as DocumentType).description;
    const edit = async () => {
      try {
        const detail =
          kind === "correspondents"
            ? await api.correspondent(item.id)
            : await api.documentType(item.id);
        const detailText =
          kind === "correspondents"
            ? (detail as Correspondent).notes || ""
            : (detail as DocumentType).description || "";
        setEditingLabel({
          kind,
          id: item.id,
          name: detail.name,
          description: detailText,
        });
      } catch (err) {
        onError(err, "Item details could not be loaded.");
      }
    };
    return (
      <Paper
        key={item.id}
        variant="outlined"
        sx={{
          p: 1.5,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 1,
          flexWrap: "wrap",
          bgcolor: "action.hover",
        }}
      >
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {item.name}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {text || "No description"}
          </Typography>
        </Box>
        <Stack direction="row">
          <Button size="small" onClick={() => void edit()}>
            Edit
          </Button>
          <Button
            size="small"
            color="error"
            onClick={() =>
              api
                .deleteLabel(kind, item.id)
                .then(() =>
                  onChange(
                    kind,
                    (kind === "correspondents" ? correspondents : types).filter(
                      (x) => x.id !== item.id,
                    ),
                  ),
                )
                .catch((err) => onError(err, "Item could not be deleted."))
            }
          >
            Delete
          </Button>
        </Stack>
      </Paper>
    );
  };

  return (
    <Stack className="reveal" sx={{ gap: 2.5, maxWidth: 920 }}>
      <Card component="section" sx={sectionSx}>
        <Typography component="h2" variant="h5">
          Correspondents and document types
        </Typography>
        <Stack direction={{ xs: "column", md: "row" }} sx={{ gap: 1.5, mt: 3 }}>
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            sx={{ flex: 1 }}
            slotProps={{ htmlInput: { "aria-label": "Metadata name" } }}
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            sx={{ flex: 1 }}
            slotProps={{ htmlInput: { "aria-label": "Metadata description" } }}
          />
          <Stack direction="row" sx={{ gap: 1, flexWrap: "wrap" }}>
            <Button
              onClick={() => void add("correspondents")}
              variant="contained"
            >
              Add correspondent
            </Button>
            <Button
              onClick={() => void add("document-types")}
              variant="contained"
              color="secondary"
            >
              Add type
            </Button>
          </Stack>
        </Stack>
      </Card>
      <Card component="section" sx={sectionSx}>
        <Typography component="h2" variant="h5">
          Correspondents
        </Typography>
        <Stack sx={{ gap: 1, mt: 2 }}>
          {correspondents.map((x) => row("correspondents", x))}
        </Stack>
      </Card>
      <Card component="section" sx={sectionSx}>
        <Typography component="h2" variant="h5">
          Document types
        </Typography>
        <Stack sx={{ gap: 1, mt: 2 }}>
          {types.map((x) => row("document-types", x))}
        </Stack>
      </Card>
      {editingLabel && (
        <Modal
          title={`Edit ${editingLabel.kind === "correspondents" ? "correspondent" : "document type"}`}
          onClose={() => setEditingLabel(null)}
        >
          <Box
            component="form"
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                if (editingLabel.kind === "correspondents") {
                  const updated = await api.updateLabel(
                    editingLabel.kind,
                    editingLabel.id,
                    {
                      name: editingLabel.name,
                      notes: editingLabel.description || undefined,
                    },
                  );
                  onChange(
                    editingLabel.kind,
                    correspondents.map((v) =>
                      v.id === updated.id ? updated : v,
                    ),
                  );
                } else {
                  const updated = await api.updateDocumentType(
                    editingLabel.id,
                    {
                      name: editingLabel.name,
                      description: editingLabel.description || undefined,
                    },
                  );
                  onChange(
                    editingLabel.kind,
                    types.map((v) => (v.id === updated.id ? updated : v)),
                  );
                }
                setEditingLabel(null);
              } catch (err) {
                setEditingLabel((cur) =>
                  cur
                    ? {
                        ...cur,
                        error:
                          err instanceof Error
                            ? err.message
                            : "Item could not be edited.",
                      }
                    : null,
                );
              }
            }}
          >
            <Stack sx={{ gap: 2, mt: 1 }}>
              <TextField
                label="Name"
                required
                autoFocus
                value={editingLabel.name}
                onChange={(e) =>
                  setEditingLabel((cur) =>
                    cur ? { ...cur, name: e.target.value } : null,
                  )
                }
              />
              <TextField
                label="Description"
                value={editingLabel.description}
                onChange={(e) =>
                  setEditingLabel((cur) =>
                    cur ? { ...cur, description: e.target.value } : null,
                  )
                }
              />
              {editingLabel.error && (
                <Alert severity="error" role="alert">
                  {editingLabel.error}
                </Alert>
              )}
            </Stack>
            <DialogActions sx={{ px: 0, mt: 3 }}>
              <Button
                type="button"
                variant="outlined"
                onClick={() => setEditingLabel(null)}
              >
                Cancel
              </Button>
              <Button type="submit" variant="contained">
                Save changes
              </Button>
            </DialogActions>
          </Box>
        </Modal>
      )}
    </Stack>
  );
}
