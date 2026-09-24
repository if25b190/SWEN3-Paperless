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
import type { Document } from "../../lib/api";
import { bytes } from "./document-utils";

export function DocumentGrid({
  documents,
  loading,
  onDelete,
  onOpen,
  onUpload,
  ready,
}: {
  documents: Document[];
  loading: boolean;
  onDelete: (id: number) => void;
  onOpen: (doc: Document) => void;
  onUpload: () => void;
  ready: boolean;
}) {
  if (loading)
    return (
      <Typography color="text.secondary" align="center" sx={{ py: 10 }}>
        Loading your library…
      </Typography>
    );
  if (!documents.length)
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
            {ready ? "Search your archive" : "Your desk is ready"}
          </Typography>
          <Typography
            color="text.secondary"
            variant="body2"
            sx={{ mt: 1, mb: 3 }}
          >
            {ready
              ? "Enter a phrase above to find a document."
              : "Upload a document to start building your library."}
          </Typography>
          <Button
            variant="contained"
            color="secondary"
            startIcon={<AddRounded />}
            onClick={onUpload}
          >
            Upload document
          </Button>
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
      {documents.map((doc) => (
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
                  label={doc.status.replaceAll("_", " ")}
                  sx={{
                    bgcolor: "action.hover",
                    fontSize: 10,
                    fontWeight: 700,
                  }}
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
              <Typography color="text.secondary" variant="caption" noWrap>
                {doc.correspondent?.name || "Unassigned"}
              </Typography>
              <Button
                size="small"
                color="error"
                startIcon={<DeleteOutlineRounded fontSize="small" />}
                aria-label={`Delete ${doc.title}`}
                onClick={() => onDelete(doc.id)}
              >
                Delete
              </Button>
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Box>
  );
}
