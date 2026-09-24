import {
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from "@mui/material";
import type { Correspondent, DocumentType } from "../../lib/api";
import { smallLabel } from "../shared/styles";

type FilterDraft = { correspondent_id: string; document_type_id: string };

export function Filters({
  draft,
  correspondents,
  types,
  onChange,
  onApply,
}: {
  draft: FilterDraft;
  correspondents: Correspondent[];
  types: DocumentType[];
  onChange: (v: FilterDraft) => void;
  onApply: () => void;
}) {
  return (
    <Stack
      direction="row"
      sx={{ gap: 1.5, alignItems: "center", flexWrap: "wrap", mb: 3 }}
    >
      <Typography color="text.secondary" sx={{ ...smallLabel, mr: 1 }}>
        Filter by
      </Typography>
      <FormControl size="small" sx={{ minWidth: 190 }}>
        <InputLabel id="filter-correspondent-label" shrink>
          Correspondent
        </InputLabel>
        <Select
          labelId="filter-correspondent-label"
          label="Correspondent"
          displayEmpty
          value={draft.correspondent_id}
          onChange={(e) =>
            onChange({ ...draft, correspondent_id: e.target.value })
          }
        >
          <MenuItem value="">All correspondents</MenuItem>
          {correspondents.map((x) => (
            <MenuItem key={x.id} value={String(x.id)}>
              {x.name}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
      <FormControl size="small" sx={{ minWidth: 170 }}>
        <InputLabel id="filter-document-type-label" shrink>
          Document type
        </InputLabel>
        <Select
          labelId="filter-document-type-label"
          label="Document type"
          displayEmpty
          value={draft.document_type_id}
          onChange={(e) =>
            onChange({ ...draft, document_type_id: e.target.value })
          }
        >
          <MenuItem value="">All types</MenuItem>
          {types.map((x) => (
            <MenuItem key={x.id} value={String(x.id)}>
              {x.name}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
      <Button variant="outlined" onClick={onApply}>
        Apply filters
      </Button>
    </Stack>
  );
}
