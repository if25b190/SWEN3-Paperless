"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  AppBar,
  Box,
  Button,
  Chip,
  DialogActions,
  Divider,
  Drawer,
  IconButton,
  InputAdornment,
  Paper,
  Stack,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
} from "@mui/material";
import { useColorScheme, useTheme } from "@mui/material/styles";
import AddRounded from "@mui/icons-material/AddRounded";
import LightModeRounded from "@mui/icons-material/LightModeRounded";
import MenuRounded from "@mui/icons-material/MenuRounded";
import NightsStayRounded from "@mui/icons-material/NightsStayRounded";
import PersonOutlineRounded from "@mui/icons-material/PersonOutlineRounded";
import SearchRounded from "@mui/icons-material/SearchRounded";
import {
  api,
  apiDelete,
  type Correspondent,
  type Document,
  type DocumentType,
  type Team,
  type User,
} from "../../lib/api";
import { LoginDialog } from "../auth/LoginDialog";
import { DocumentDetail } from "../documents/DocumentDetail";
import { DocumentGrid } from "../documents/DocumentGrid";
import { Filters } from "../documents/Filters";
import { Pagination } from "../documents/Pagination";
import { UploadDialog } from "../documents/UploadDialog";
import { People } from "../people/People";
import { Settings } from "../settings/Settings";
import { Modal } from "../shared/Modal";
import { drawerWidth, smallLabel } from "../shared/styles";
import { navigation, WorkspaceNavigation } from "./WorkspaceNavigation";
import type { Notice, View } from "./types";

export default function Workspace() {
  const [view, setView] = useState<View>("library");
  const [user, setUser] = useState<User | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [selected, setSelected] = useState<Document | null>(null);
  const [page, setPage] = useState({ page: 0, total_pages: 0 });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const [loginOpen, setLoginOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [queryText, setQueryText] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [searchReady, setSearchReady] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [draft, setDraft] = useState({
    correspondent_id: "",
    document_type_id: "",
  });
  const [filters, setFilters] = useState(draft);
  const [correspondents, setCorrespondents] = useState<Correspondent[]>([]);
  const [types, setTypes] = useState<DocumentType[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [documentToDelete, setDocumentToDelete] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState("");
  const [deleting, setDeleting] = useState(false);
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up("lg"));
  const { mode, setMode, systemMode } = useColorScheme();
  const darkMode = (mode === "system" ? systemMode : mode) === "dark";
  const fail = (error: unknown, fallback: string) =>
    setNotice({
      tone: "error",
      text: error instanceof Error ? error.message : fallback,
    });
  const loadDocs = useCallback(
    async (next = 0, applied = filters) => {
      setLoading(true);
      try {
        const result = await api.documents({
          page: next,
          size: 12,
          sort: "created_at,desc",
          ...applied,
        });
        setDocuments(result.items);
        setPage({
          page: result.pagination.page,
          total_pages: result.pagination.total_pages,
        });
      } catch (e) {
        fail(e, "The library could not be loaded.");
      } finally {
        setLoading(false);
      }
    },
    [filters],
  );
  const loadMeta = useCallback(async () => {
    try {
      const [c, t, team, people] = await Promise.all([
        api.labels("correspondents"),
        api.labels("document-types"),
        api.teams(),
        api.users(),
      ]);
      setCorrespondents(c.items as Correspondent[]);
      setTypes(t.items as DocumentType[]);
      setTeams(team.items);
      setUsers(people.items);
    } catch (e) {
      fail(e, "Workspace data could not be loaded.");
    }
  }, []);
  useEffect(() => {
    const boot = async () => {
      if (!localStorage.getItem("paperless_token")) return;
      try {
        const current = await api.me();
        setUser(current);
        await Promise.all([loadDocs(), loadMeta()]);
      } catch (e) {
        fail(e, "Your session could not be restored.");
      }
    };
    void boot();
    const expired = () => {
      setUser(null);
      setDocuments([]);
      setSelected(null);
      setUploadOpen(false);
      setLoginOpen(true);
    };
    window.addEventListener("paperless:unauthorized", expired);
    return () => window.removeEventListener("paperless:unauthorized", expired);
  }, [loadDocs, loadMeta]);
  const search = async (
    event: React.FormEvent,
    next = 0,
    query = queryText,
  ) => {
    event.preventDefault();
    const submitted = query.trim();
    if (!submitted) return;
    if (!next) setSubmittedQuery(submitted);
    setSearchLoading(true);
    setNotice(null);
    try {
      const result = await api.search({
        query: submitted,
        fuzzy: true,
        page: next,
        size: 12,
      });
      setDocuments(result.items.map((item) => item.document));
      setPage({
        page: result.pagination.page,
        total_pages: result.pagination.total_pages,
      });
      setSearchReady(true);
      setView("search");
    } catch (e) {
      fail(e, "Search results could not be loaded.");
    } finally {
      setSearchLoading(false);
    }
  };
  const openDocument = (doc: Document) =>
    api
      .document(doc.id)
      .then(setSelected)
      .catch((e) => fail(e, "Document details could not be loaded."));
  const removeDocument = (id: number) => {
    setDocumentToDelete(id);
    setDeleteError("");
  };
  const selectView = (next: View) => {
    setMobileNavOpen(false);
    setView(next);
    if (next === "search") {
      setQueryText("");
      setSubmittedQuery("");
      setDocuments([]);
      setSearchReady(true);
      setPage({ page: 0, total_pages: 0 });
    }
    if (next === "library") {
      setSearchReady(false);
      void loadDocs();
    }
  };
  const headings = {
    library: "Good documents, within reach.",
    search: submittedQuery
      ? `Results for “${submittedQuery}”`
      : "Search your archive",
    people: "Your people",
    settings: "Workspace settings",
  };
  const captions = {
    library: "Everything you need to keep your documents in order.",
    search: "Find the right file without digging through folders.",
    people: "Manage the people and teams in your workspace.",
    settings: "Keep your document labels organized.",
  };

  return (
    <>
      <Box
        id="workspace-shell"
        component="main"
        sx={{ minHeight: "100vh", bgcolor: "background.default" }}
      >
        <Drawer
          variant={desktop ? "permanent" : "temporary"}
          open={desktop || mobileNavOpen}
          onClose={() => setMobileNavOpen(false)}
          slotProps={{
            paper: {
              className: "workspace-sidebar",
              sx: { width: drawerWidth, border: 0, backgroundImage: "none" },
            },
          }}
        >
          <WorkspaceNavigation view={view} user={user} onSelect={selectView} />
        </Drawer>
        <Box sx={{ ml: { lg: `${drawerWidth}px` }, minWidth: 0 }}>
          <AppBar
            position="sticky"
            color="inherit"
            elevation={0}
            sx={{
              bgcolor: "background.paper",
              borderBottom: "1px solid",
              borderColor: "divider",
              backgroundImage: "none",
            }}
          >
            <Toolbar
              sx={{
                minHeight: { xs: 64, sm: 72 },
                px: { xs: 2, sm: 4, lg: 6 },
                gap: { xs: 1, sm: 2 },
              }}
            >
              {!desktop && (
                <IconButton
                  edge="start"
                  aria-label="Open navigation"
                  aria-expanded={mobileNavOpen}
                  onClick={() => setMobileNavOpen(true)}
                  sx={{ color: "text.primary" }}
                >
                  <MenuRounded />
                </IconButton>
              )}
              <Typography
                sx={{
                  fontSize: { xs: 16, sm: 18 },
                  fontWeight: 650,
                  letterSpacing: "-.025em",
                  whiteSpace: "nowrap",
                }}
              >
                {navigation.find((item) => item.view === view)?.label}
              </Typography>
              <Box sx={{ flex: 1 }} />
              {mode === undefined ? (
                <Box aria-hidden="true" sx={{ width: 40, height: 40 }} />
              ) : (
                <Tooltip title={darkMode ? "Use light mode" : "Use dark mode"}>
                  <IconButton
                    aria-label="Dark mode"
                    aria-pressed={darkMode}
                    onClick={() => setMode(darkMode ? "light" : "dark")}
                    sx={{ color: "text.secondary" }}
                  >
                    {darkMode ? <LightModeRounded /> : <NightsStayRounded />}
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title={user ? "Account" : "Sign in"}>
                <IconButton
                  aria-label={user ? "Account" : "Sign in"}
                  onClick={() => setLoginOpen(true)}
                  sx={{ color: "text.secondary" }}
                >
                  <PersonOutlineRounded />
                </IconButton>
              </Tooltip>
              <Divider
                orientation="vertical"
                flexItem
                sx={{ mx: { xs: 0.5, sm: 1 }, my: 1.5 }}
              />
              <Button
                variant="contained"
                color="secondary"
                startIcon={<AddRounded />}
                onClick={() => setUploadOpen(true)}
                aria-label="Upload document"
                sx={{
                  minWidth: { xs: 42, sm: 0 },
                  width: { xs: 42, sm: "auto" },
                  height: 42,
                  px: { xs: 0, sm: 2 },
                  whiteSpace: "nowrap",
                  "& .MuiButton-startIcon": { m: { xs: 0, sm: "0 8px 0 0" } },
                }}
              >
                <Box
                  component="span"
                  sx={{ display: { xs: "none", sm: "inline" } }}
                >
                  Upload document
                </Box>
              </Button>
            </Toolbar>
          </AppBar>
          <Box
            sx={{
              maxWidth: 1400,
              mx: "auto",
              px: { xs: 2.5, sm: 5, lg: 6 },
              py: { xs: 4, lg: 6 },
            }}
          >
            <Stack
              className="reveal"
              direction={{ xs: "column", sm: "row" }}
              sx={{
                gap: 3,
                alignItems: { sm: "flex-end" },
                justifyContent: "space-between",
                mb: 4,
              }}
            >
              <Box>
                <Typography
                  color="primary.main"
                  sx={{ ...smallLabel, mb: 1.5, letterSpacing: ".12em" }}
                >
                  {view === "library"
                    ? "Your documents"
                    : view === "people"
                      ? "People and teams"
                      : view === "settings"
                        ? "Manage your workspace"
                        : "Find documents"}
                </Typography>
                <Typography
                  component="h1"
                  variant="h1"
                  sx={{
                    fontSize: { xs: 32, sm: 42, lg: 48 },
                    lineHeight: 1.18,
                    maxWidth: 750,
                  }}
                >
                  {headings[view]}
                </Typography>
                <Typography
                  color="text.secondary"
                  variant="body2"
                  sx={{ mt: 1.5 }}
                >
                  {captions[view]}
                </Typography>
              </Box>
              {view === "library" && (
                <Chip
                  variant="outlined"
                  label={`${documents.length} documents on this page`}
                  sx={{ alignSelf: { xs: "flex-start", sm: "flex-end" } }}
                />
              )}
            </Stack>
            {notice && (
              <Alert severity={notice.tone} role="alert" sx={{ mb: 3 }}>
                {notice.text}
              </Alert>
            )}
            {view === "library" || view === "search" ? (
              <Box className="reveal reveal-later">
                <Paper
                  className="search-hero"
                  elevation={0}
                  sx={{
                    border: "1px solid",
                    borderColor: "divider",
                    borderRadius: 4,
                    p: { xs: 2.5, sm: 4 },
                    mb: 3.5,
                  }}
                >
                  <Typography color="text.secondary" sx={smallLabel}>
                    {view === "search" ? "Find a document" : "Quick search"}
                  </Typography>
                  <Typography
                    variant="h5"
                    component="h2"
                    sx={{ my: 1, fontSize: 21 }}
                  >
                    Your files, a little easier to find.
                  </Typography>
                  <Paper
                    component="form"
                    onSubmit={(e) => search(e)}
                    elevation={0}
                    sx={{
                      mt: 2.5,
                      p: 1,
                      border: "1px solid",
                      borderColor: "divider",
                      borderRadius: 2,
                      display: "flex",
                      flexWrap: { xs: "wrap", sm: "nowrap" },
                      gap: 1,
                    }}
                  >
                    <TextField
                      slotProps={{
                        htmlInput: { "aria-label": "Search documents" },
                        input: {
                          startAdornment: (
                            <InputAdornment position="start">
                              <SearchRounded color="action" fontSize="small" />
                            </InputAdornment>
                          ),
                        },
                      }}
                      placeholder="Search by title, text, or filename"
                      value={queryText}
                      onChange={(e) => setQueryText(e.target.value)}
                      fullWidth
                      sx={{ "& fieldset": { border: 0 } }}
                    />
                    <Button
                      type="submit"
                      variant="contained"
                      color="secondary"
                      disabled={searchLoading}
                      sx={{ width: { xs: "100%", sm: "auto" }, flexShrink: 0 }}
                    >
                      {searchLoading ? "Searching…" : "Search"}
                    </Button>
                  </Paper>
                </Paper>
                {view === "library" && (
                  <Filters
                    draft={draft}
                    correspondents={correspondents}
                    types={types}
                    onChange={setDraft}
                    onApply={() => {
                      setFilters(draft);
                      setSearchReady(false);
                      void loadDocs(0, draft);
                    }}
                  />
                )}
                {searchLoading ? (
                  <Typography
                    color="text.secondary"
                    align="center"
                    sx={{ py: 10 }}
                  >
                    Loading search results…
                  </Typography>
                ) : (
                  <DocumentGrid
                    documents={documents}
                    loading={loading}
                    onDelete={removeDocument}
                    onOpen={openDocument}
                    onUpload={() => setUploadOpen(true)}
                    ready={searchReady}
                  />
                )}
                {page.total_pages > 1 && (
                  <Pagination
                    page={page.page}
                    total={page.total_pages}
                    loading={loading || searchLoading}
                    onChange={(next) =>
                      searchReady
                        ? void search(
                            new Event("submit") as unknown as React.FormEvent,
                            next,
                            submittedQuery,
                          )
                        : void loadDocs(next)
                    }
                  />
                )}
              </Box>
            ) : view === "people" ? (
              <People
                users={users}
                teams={teams}
                onUsers={setUsers}
                onTeams={setTeams}
                onError={fail}
              />
            ) : (
              <Settings
                correspondents={correspondents}
                types={types}
                onChange={(kind, items) =>
                  kind === "correspondents"
                    ? setCorrespondents(items as Correspondent[])
                    : setTypes(items as DocumentType[])
                }
                onError={fail}
              />
            )}
          </Box>
        </Box>
      </Box>
      {loginOpen && (
        <LoginDialog
          onClose={() => setLoginOpen(false)}
          onLogin={(u) => {
            setUser(u);
            setLoginOpen(false);
            void Promise.all([loadDocs(), loadMeta()]);
          }}
        />
      )}
      {uploadOpen && (
        <UploadDialog
          onClose={() => setUploadOpen(false)}
          onUploaded={(doc) => {
            setDocuments((items) => [doc, ...items]);
            setUploadOpen(false);
          }}
        />
      )}
      {selected && (
        <DocumentDetail
          document={selected}
          correspondents={correspondents}
          types={types}
          onClose={() => setSelected(null)}
          onSaved={(doc) => {
            setSelected(doc);
            setDocuments((items) =>
              items.map((item) => (item.id === doc.id ? doc : item)),
            );
          }}
          onDelete={removeDocument}
        />
      )}
      {documentToDelete !== null && (
        <Modal
          title="Delete document"
          onClose={() => {
            setDocumentToDelete(null);
            setDeleteError("");
          }}
        >
          <Stack sx={{ gap: 2 }}>
            <Typography variant="body1">
              Are you sure you want to delete this document? This action cannot
              be undone.
            </Typography>
            {deleteError && (
              <Alert severity="error" role="alert">
                {deleteError}
              </Alert>
            )}
          </Stack>
          <DialogActions sx={{ px: 0, mt: 3 }}>
            <Button
              type="button"
              variant="outlined"
              onClick={() => {
                setDocumentToDelete(null);
                setDeleteError("");
              }}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="contained"
              color="error"
              disabled={deleting}
              onClick={async () => {
                setDeleting(true);
                try {
                  await apiDelete(`/documents/${documentToDelete}`);
                  setDocuments((items) =>
                    items.filter((item) => item.id !== documentToDelete),
                  );
                  setSelected(null);
                  setDocumentToDelete(null);
                } catch (e) {
                  setDeleteError(
                    e instanceof Error
                      ? e.message
                      : "Document could not be deleted.",
                  );
                } finally {
                  setDeleting(false);
                }
              }}
            >
              {deleting ? "Deleting…" : "Delete"}
            </Button>
          </DialogActions>
        </Modal>
      )}
    </>
  );
}
