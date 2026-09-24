import { useState } from "react";
import {
  Box,
  Button,
  Card,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { api, type Correspondent, type DocumentType } from "../../lib/api";
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
        const next = prompt("Name", detail.name);
        const detailText =
          kind === "correspondents"
            ? (detail as Correspondent).notes
            : (detail as DocumentType).description;
        const value = prompt("Description", detailText || "");
        if (!next) return;
        if (kind === "correspondents") {
          const updated = await api.updateLabel(kind, item.id, {
            name: next,
            notes: value || undefined,
          });
          onChange(
            kind,
            correspondents.map((v) => (v.id === updated.id ? updated : v)),
          );
        } else {
          const updated = await api.updateDocumentType(item.id, {
            name: next,
            description: value || undefined,
          });
          onChange(
            kind,
            types.map((v) => (v.id === updated.id ? updated : v)),
          );
        }
      } catch (err) {
        onError(err, "Item could not be edited.");
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
    </Stack>
  );
}
