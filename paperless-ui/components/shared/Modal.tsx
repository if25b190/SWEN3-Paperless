import {
  Box,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
} from "@mui/material";
import CloseRounded from "@mui/icons-material/CloseRounded";
import type { ReactNode } from "react";
import { smallLabel } from "./styles";

export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <Dialog
      open
      onClose={onClose}
      aria-labelledby="modal-title"
      fullWidth
      maxWidth="sm"
      scroll="paper"
      slotProps={{ paper: { sx: { p: { xs: 1, sm: 2 } } } }}
    >
      <DialogTitle
        id="modal-title"
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "start",
          gap: 2,
          pb: 0,
        }}
      >
        <Box>
          <Typography color="text.secondary" sx={smallLabel}>
            Paperless
          </Typography>
          <Typography
            component="span"
            variant="h5"
            sx={{ display: "block", mt: 1 }}
          >
            {title}
          </Typography>
        </Box>
        <IconButton
          aria-label={`Close ${title}`}
          onClick={onClose}
          size="small"
        >
          <CloseRounded fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent sx={{ pt: "16px !important" }}>{children}</DialogContent>
    </Dialog>
  );
}
