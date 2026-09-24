import {
  Avatar,
  Box,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import DashboardRounded from "@mui/icons-material/DashboardRounded";
import Groups2Outlined from "@mui/icons-material/Groups2Outlined";
import SearchRounded from "@mui/icons-material/SearchRounded";
import SpaRounded from "@mui/icons-material/SpaRounded";
import TuneRounded from "@mui/icons-material/TuneRounded";
import type { User } from "../../lib/api";
import { smallLabel } from "../shared/styles";
import type { View } from "./types";

export const navigation = [
  { view: "library", label: "Library", icon: <DashboardRounded /> },
  { view: "search", label: "Search", icon: <SearchRounded /> },
  { view: "people", label: "People", icon: <Groups2Outlined /> },
  { view: "settings", label: "Settings", icon: <TuneRounded /> },
] as const;

const initials = (name = "You") =>
  name
    .split(/[ _-]/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

export function WorkspaceNavigation({
  view,
  user,
  onSelect,
}: {
  view: View;
  user: User | null;
  onSelect: (view: View) => void;
}) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        px: 2.5,
        py: 3.5,
        color: "#f3f8ee",
        position: "relative",
        zIndex: 1,
      }}
    >
      <Stack
        direction="row"
        spacing={1.5}
        sx={{ alignItems: "center", mb: 6, px: 1 }}
      >
        <Avatar
          variant="rounded"
          sx={{
            bgcolor: "#d8ef83",
            color: "#183729",
            width: 42,
            height: 42,
            borderRadius: 2,
          }}
        >
          <SpaRounded />
        </Avatar>
        <Box>
          <Typography
            sx={{
              fontWeight: 700,
              letterSpacing: "-.055em",
              fontSize: 18,
              lineHeight: 1.2,
            }}
          >
            paperless
            <Box component="span" sx={{ color: "#d8ef83" }}>
              .
            </Box>
          </Typography>
          <Typography
            sx={{ fontSize: 10, letterSpacing: ".12em", color: "#bcd1c0" }}
          >
            DOCUMENT DESK
          </Typography>
        </Box>
      </Stack>
      <Typography
        sx={{ ...smallLabel, fontSize: 10, color: "#bcd1c0", px: 2, mb: 1.25 }}
      >
        Browse
      </Typography>
      <List component="nav" aria-label="Main navigation" disablePadding>
        {navigation.map(({ view: key, label, icon }) => (
          <ListItemButton
            component="button"
            type="button"
            key={key}
            selected={view === key}
            aria-current={view === key ? "page" : undefined}
            onClick={() => onSelect(key)}
            sx={{
              width: "100%",
              borderRadius: 0,
              px: 2,
              py: 1.6,
              color: view === key ? "#e0f1aa" : "#c3d5c6",
              borderBottom: "1px solid #ffffff18",
              "&.Mui-selected, &.Mui-selected:hover": {
                bgcolor: "#ffffff0e",
                color: "#e0f1aa",
              },
              "&:hover": { bgcolor: "#ffffff0b", color: "#fff" },
            }}
          >
            <ListItemIcon sx={{ minWidth: 40, color: "inherit" }}>
              {icon}
            </ListItemIcon>
            <ListItemText
              primary={label}
              slotProps={{
                primary: {
                  sx: { fontSize: 14, fontWeight: view === key ? 650 : 500 },
                },
              }}
            />
          </ListItemButton>
        ))}
      </List>
      <Box sx={{ flex: 1 }} />
      <Divider sx={{ borderColor: "#ffffff2d", mb: 2.5 }} />
      <Stack
        direction="row"
        spacing={1.25}
        sx={{ alignItems: "center", px: 1 }}
      >
        <Avatar
          sx={{
            bgcolor: "#d8ef83",
            color: "#183729",
            width: 38,
            height: 38,
            fontSize: 13,
            fontWeight: 700,
          }}
        >
          {initials(user?.username)}
        </Avatar>
        <Box sx={{ minWidth: 0 }}>
          <Typography noWrap variant="body2" sx={{ fontWeight: 600 }}>
            {user?.username || "Guest reader"}
          </Typography>
          <Typography noWrap sx={{ fontSize: 11, color: "#bcd1c0" }}>
            {user?.email || "Sign in to sync"}
          </Typography>
        </Box>
      </Stack>
    </Box>
  );
}
