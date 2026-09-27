"use client";

import {
  Button,
  MenuItem,
  TextField,
  Stack,
  Typography,
} from "@mui/material";
import type { DocumentType, Team } from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { smallLabel } from "../shared/styles";

export type FilterDraft = { document_type_id: string; team: string; sort: string };

export function Filters({
  draft,
  types,
  teams,
  onChange,
  onApply,
}: {
  draft: FilterDraft;
  types: DocumentType[];
  teams: Team[];
  onChange: (v: FilterDraft) => void;
  onApply: () => void;
}) {
  const { t } = useI18n();
  return (
    <Stack
      direction="row"
      sx={{ gap: 1.5, alignItems: "center", flexWrap: "wrap", mb: 3 }}
    >
      <Typography color="text.secondary" sx={{ ...smallLabel, mr: 1 }}>
        {t("filters.filter_by")}
      </Typography>
      <TextField
        select
        size="small"
        sx={{ minWidth: 180 }}
        label={t("filters.document_type")}
        value={draft.document_type_id}
        onChange={(e) => onChange({ ...draft, document_type_id: e.target.value })}
      >
        <MenuItem value="">{t("filters.all_types")}</MenuItem>
        {types.map((x) => (
          <MenuItem key={x.id} value={x.id}>
            {x.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        sx={{ minWidth: 170 }}
        label={t("filters.team")}
        value={draft.team}
        onChange={(e) => onChange({ ...draft, team: e.target.value })}
      >
        <MenuItem value="">{t("filters.all_teams")}</MenuItem>
        <MenuItem value="private">{t("filters.private")}</MenuItem>
        {teams.map((x) => (
          <MenuItem key={x.id} value={x.id}>
            {x.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        sx={{ minWidth: 170 }}
        label={t("filters.sort")}
        value={draft.sort}
        onChange={(e) => onChange({ ...draft, sort: e.target.value })}
      >
        <MenuItem value="created_at,desc">{t("sort.created_desc")}</MenuItem>
        <MenuItem value="created_at,asc">{t("sort.created_asc")}</MenuItem>
        <MenuItem value="title,asc">{t("sort.title_asc")}</MenuItem>
        <MenuItem value="title,desc">{t("sort.title_desc")}</MenuItem>
        <MenuItem value="file_size,desc">{t("sort.size_desc")}</MenuItem>
        <MenuItem value="file_size,asc">{t("sort.size_asc")}</MenuItem>
      </TextField>
      <Button variant="outlined" onClick={onApply}>
        {t("common.apply")}
      </Button>
    </Stack>
  );
}
