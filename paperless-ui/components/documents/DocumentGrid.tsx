"use client";

import {
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Stack,
  Typography,
} from "@mui/material";
import AddRounded from "@mui/icons-material/AddRounded";
import DeleteOutlineRounded from "@mui/icons-material/DeleteOutlineRounded";
import DescriptionOutlined from "@mui/icons-material/DescriptionOutlined";
import Inventory2Outlined from "@mui/icons-material/Inventory2Outlined";
import PictureAsPdfOutlined from "@mui/icons-material/PictureAsPdfOutlined";
import type { Document, Team } from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { bytes, parseHighlights, statusTone } from "./document-utils";

export type DocumentGridItem = {
  document: Document;
  score?: number;
  highlights?: string[];
};

export function DocumentGrid({
  items,
  loading,
  loadingLabel,
  teams,
  emptyState,
  onDelete,
  onOpen,
  onUpload,
}: {
  items: DocumentGridItem[];
  loading: boolean;
  loadingLabel: string;
  teams: Team[];
  emptyState: "library" | "search";
  onDelete: (id: string) => void;
  onOpen: (doc: Document) => void;
  onUpload: () => void;
}) {
  const { t } = useI18n();
  const teamName = (doc: Document) => {
    if (!doc.team_id) return t("filters.private");
    return teams.find((team) => team.id === doc.team_id)?.name || t("filters.private");
  };

  if (loading)
    return (
      <Typography color="text.secondary" align="center" sx={{ py: 10 }}>
        {loadingLabel}
      </Typography>
    );
  if (!items.length)
    return (
      <Card
        variant="outlined"
        sx={{ textAlign: "center", borderStyle: "dashed", boxShadow: "none" }}
      >
        <CardContent sx={{ py: "60px !important", px: 3 }}>
          <Avatar
            variant="rounded"
            sx={{
              mx: "auto",
              mb: 2.5,
              width: 62,
              height: 62,
              bgcolor: "secondary.main",
              color: "secondary.contrastText",
            }}
          >
            <Inventory2Outlined fontSize="large" />
          </Avatar>
          <Typography component="h2" variant="h5">
            {emptyState === "search" ? t("grid.search_empty") : t("grid.library_empty")}
          </Typography>
          <Typography
            color="text.secondary"
            variant="body2"
            sx={{ mt: 1, mb: 3 }}
          >
            {emptyState === "search" ? t("grid.search_empty_hint") : t("grid.library_empty_hint")}
          </Typography>
          {emptyState !== "search" && (
            <Button
              variant="contained"
              color="secondary"
              startIcon={<AddRounded />}
              onClick={onUpload}
            >
              {t("upload.title")}
            </Button>
          )}
        </CardContent>
      </Card>
    );
  return (
    <Box
      sx={{
        display: "grid",
        gap: 2,
        gridTemplateColumns: {
          xs: "1fr",
          sm: "repeat(2,minmax(0,1fr))",
          xl: "repeat(3,minmax(0,1fr))",
        },
      }}
    >
      {items.map((item) => {
        const doc = item.document;
        return (
          <Card
            key={doc.id}
            component="article"
            sx={{
              display: "flex",
              flexDirection: "column",
              minHeight: 265,
              transition: "transform .25s, box-shadow .25s",
              "&:hover": { transform: "translateY(-4px)", boxShadow: 6 },
            }}
          >
            <CardContent
              sx={{ display: "flex", flexDirection: "column", flex: 1, p: 3 }}
            >
              <Button
                onClick={() => onOpen(doc)}
                sx={{
                  textAlign: "left",
                  p: 0,
                  flex: 1,
                  display: "block",
                  color: "text.primary",
                  "&:hover": {
                    bgcolor: "transparent",
                    textDecoration: "underline",
                    textDecorationColor: "secondary.main",
                    textUnderlineOffset: 4,
                  },
                }}
              >
                <Stack
                  direction="row"
                  sx={{
                    alignItems: "start",
                    justifyContent: "space-between",
                    mb: 3.5,
                  }}
                >
                  <Avatar
                    variant="rounded"
                    sx={{ bgcolor: "action.hover", color: "text.primary" }}
                  >
                    {doc.content_type.includes("pdf") ? (
                      <PictureAsPdfOutlined />
                    ) : (
                      <DescriptionOutlined />
                    )}
                  </Avatar>
                  <Chip
                    size="small"
                    color={statusTone[doc.status]}
                    variant={statusTone[doc.status] === "default" ? "outlined" : "filled"}
                    label={t(`status.${doc.status}`)}
                    sx={{ fontSize: 10, fontWeight: 700 }}
                  />
                </Stack>
                <Typography
                  component="h2"
                  variant="h6"
                  sx={{
                    fontWeight: 650,
                    lineHeight: 1.3,
                    display: "-webkit-box",
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: "vertical",
                    overflow: "hidden",
                  }}
                >
                  {doc.title}
                </Typography>
                <Typography
                  color="text.secondary"
                  variant="caption"
                  noWrap
                  sx={{ display: "block", mt: 1.5 }}
                >
                  {doc.original_filename} · {bytes(doc.file_size)}
                </Typography>
                {item.highlights && item.highlights.length > 0 ? (
                  <Typography
                    color="text.secondary"
                    variant="caption"
                    sx={{ display: "block", mt: 1 }}
                  >
                    {parseHighlights(item.highlights[0]).map((seg, index) =>
                      seg.emphasized ? (
                        <em key={index}>{seg.text}</em>
                      ) : (
                        <span key={index}>{seg.text}</span>
                      ),
                    )}
                  </Typography>
                ) : null}
              </Button>
              <Divider sx={{ my: 2 }} />
              <Stack
                direction="row"
                sx={{
                  gap: 1,
                  alignItems: "center",
                  justifyContent: "space-between",
                }}
              >
                <Stack direction="row" spacing={1} sx={{ alignItems: "center", overflow: "hidden" }}>
                  <Typography color="text.secondary" variant="caption" noWrap>
                    {teamName(doc)}
                  </Typography>
                  {item.score !== undefined && (
                    <Chip
                      size="small"
                      variant="outlined"
                      sx={{ height: 20, fontSize: 10 }}
                      label={`${t("search.relevance")} ${item.score.toFixed(2)}`}
                    />
                  )}
                </Stack>
                <Button
                  size="small"
                  color="error"
                  startIcon={<DeleteOutlineRounded fontSize="small" />}
                  aria-label={`${t("common.delete")} ${doc.title}`}
                  onClick={() => onDelete(doc.id)}
                >
                  {t("common.delete")}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        );
      })}
    </Box>
  );
}
