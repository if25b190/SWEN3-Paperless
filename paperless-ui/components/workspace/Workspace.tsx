"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  AppBar,
  Box,
  Button,
  Chip,
  DialogActions,
  Divider,
  Drawer,
  FormControlLabel,
  IconButton,
  InputAdornment,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
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
  isProcessing,
  type Document,
  type DocumentType,
  type SearchResultItem,
  type Team,
  type TeamMembership,
  type User,
} from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import type { Locale } from "../../lib/i18n/translations";
import { describeError } from "../../lib/toast/errors";
import { useToast } from "../../lib/toast/ToastProvider";
import { LoginDialog } from "../auth/LoginDialog";
import { DocumentDetail } from "../documents/DocumentDetail";
import { DocumentGrid, type DocumentGridItem } from "../documents/DocumentGrid";
import { Filters, type FilterDraft } from "../documents/Filters";
import { Pagination } from "../documents/Pagination";
import { UploadDialog } from "../documents/UploadDialog";
import { People } from "../people/People";
import { Settings } from "../settings/Settings";
import { Modal } from "../shared/Modal";
import { drawerWidth, smallLabel } from "../shared/styles";
import { navigation, WorkspaceNavigation } from "./WorkspaceNavigation";
import type { View } from "./types";

const PAGE_SIZE = 12;
const POLL_INTERVAL_MS = 5000;

export default function Workspace() {
  const { t, locale, setLocale } = useI18n();
  const { toast } = useToast();
  const [view, setView] = useState<View>("library");
  const [user, setUser] = useState<User | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [searchItems, setSearchItems] = useState<SearchResultItem[]>([]);
  const [selected, setSelected] = useState<Document | null>(null);
  const [page, setPage] = useState({ page: 0, total_pages: 0 });
  const [loading, setLoading] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [queryText, setQueryText] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [fuzzy, setFuzzy] = useState(true);
  const [searchReady, setSearchReady] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [draft, setDraft] = useState<FilterDraft>({ document_type_id: "", team: "", sort: "created_at,desc" });
  const [filters, setFilters] = useState<FilterDraft>(draft);
  const [types, setTypes] = useState<DocumentType[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [memberships, setMemberships] = useState<TeamMembership[]>([]);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [documentToDelete, setDocumentToDelete] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up("lg"));
  const { mode, setMode, systemMode } = useColorScheme();
  const darkMode = (mode === "system" ? systemMode : mode) === "dark";
  const booted = useRef(false);

  const loadDocs = useCallback(
    async (next = 0, applied = filters) => {
      setLoading(true);
      try {
        const result = await api.documents({
          page: next,
          size: PAGE_SIZE,
          sort: applied.sort || "created_at,desc",
          document_type_id: applied.document_type_id || null,
        });
        setDocuments(result.items);
        setPage({ page: result.pagination.page, total_pages: result.pagination.total_pages });
      } catch (e) {
        toast("error", describeError(e, t));
      } finally {
        setLoading(false);
      }
    },
    [filters, toast, t],
  );

  const loadMeta = useCallback(
    async (current: User | null) => {
      try {
        const [typeResult, teamResult] = await Promise.all([
          api.documentTypes(),
          api.teams(),
        ]);
        setTypes(typeResult.items);
        setTeams(teamResult.items);
        if (current) {
          const mine = await api.userTeams(current.id);
          setMemberships(mine.items);
        } else {
          setMemberships([]);
        }
      } catch (e) {
        toast("error", describeError(e, t));
      }
    },
    [toast, t],
  );

  useEffect(() => {
    if (booted.current) return;
    booted.current = true;
    const boot = async () => {
      if (!localStorage.getItem("paperless_token")) return;
      try {
        const current = await api.me();
        setUser(current);
        await Promise.all([loadDocs(), loadMeta(current)]);
      } catch {
        toast("error", t("messages.session_failed"));
      }
    };
    void boot();
    const expired = () => {
      setUser(null);
      setDocuments([]);
      setSearchItems([]);
      setSelected(null);
      setUploadOpen(false);
      setLoginOpen(true);
      toast("error", t("errors.session_expired"));
    };
    window.addEventListener("paperless:unauthorized", expired);
    return () => window.removeEventListener("paperless:unauthorized", expired);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const search = useCallback(
    async (event: React.FormEvent | null, next = 0, query = queryText) => {
      event?.preventDefault();
      const submitted = query.trim();
      if (!submitted) return;
      if (!next) setSubmittedQuery(submitted);
      setSearchLoading(true);
      try {
        const result = await api.search({ query: submitted, fuzzy, page: next, size: PAGE_SIZE });
        setSearchItems(result.items);
        setPage({ page: result.pagination.page, total_pages: result.pagination.total_pages });
        setSearchReady(true);
        setView("search");
      } catch (e) {
        toast("error", describeError(e, t));
      } finally {
        setSearchLoading(false);
      }
    },
    [queryText, fuzzy, toast, t],
  );

  const openDocument = (doc: Document) =>
    api
      .document(doc.id)
      .then(setSelected)
      .catch((e) => toast("error", describeError(e, t)));

  const removeDocument = (id: string) => setDocumentToDelete(id);

  const selectView = (next: View) => {
    setMobileNavOpen(false);
    setView(next);
    if (next === "search") {
      setQueryText("");
      setSubmittedQuery("");
      setSearchItems([]);
      setSearchReady(false);
      setPage({ page: 0, total_pages: 0 });
    }
    if (next === "library") {
      void loadDocs();
    }
  };

  const visibleDocuments = filters.team
    ? documents.filter((doc) => (filters.team === "private" ? !doc.team_id : doc.team_id === filters.team))
    : documents;

  const writableTeams = user
    ? teams.filter(
        (team) =>
          team.owner_id === user.id ||
          memberships.some((m) => m.team.id === team.id && (m.role === "ADMIN" || m.role === "READ_WRITE")),
      )
    : [];

  const listProcessing =
    view === "search" && searchReady
      ? searchItems.some((item) => isProcessing(item.document.status))
      : view === "library"
        ? documents.some((doc) => isProcessing(doc.status))
        : false;

  useEffect(() => {
    if (!user || !listProcessing) return;
    const timer = setInterval(() => {
      if (view === "library") void loadDocs(page.page);
      if (view === "search") void search(null, page.page, submittedQuery);
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [user, view, listProcessing, page.page, submittedQuery, loadDocs, search]);

  useEffect(() => {
    if (!selected || !isProcessing(selected.status)) return;
    const timer = setInterval(() => {
      api
        .document(selected.id)
        .then((fresh) => {
          setSelected(fresh);
          setDocuments((items) =>
            items.map((item) => (item.id === fresh.id ? { ...item, status: fresh.status, summary: fresh.summary, ocr_content: fresh.ocr_content } : item)),
          );
        })
        .catch(() => {});
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [selected]);

  const headings: Record<View, string> = {
    library: t("headings.library"),
    search: submittedQuery ? t("headings.search_results", { query: submittedQuery }) : t("headings.search"),
    people: t("headings.teams"),
    settings: t("headings.settings"),
  };
  const captions: Record<View, string> = {
    library: t("captions.library"),
    search: t("captions.search"),
    people: t("captions.teams"),
    settings: t("captions.settings"),
  };
  const smalls: Record<View, string> = {
    library: t("workspace.your_documents"),
    search: t("workspace.find_documents"),
    people: t("workspace.teams_label"),
    settings: t("workspace.manage_workspace"),
  };

  const gridItems: DocumentGridItem[] =
    view === "search"
      ? searchItems.map((item) => ({ document: item.document, score: item.score, highlights: item.highlights }))
      : visibleDocuments.map((document) => ({ document }));

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
                  aria-label={t("nav.open")}
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
                {t(navigation.find((item) => item.view === view)?.labelKey ?? "nav.library")}
              </Typography>
              <Box sx={{ flex: 1 }} />
              <Select
                size="small"
                value={locale}
                onChange={(e) => setLocale(e.target.value as Locale)}
                aria-label={t("nav.language")}
                sx={{ minWidth: 62 }}
              >
                <MenuItem value="en">EN</MenuItem>
                <MenuItem value="de">DE</MenuItem>
              </Select>
              {mode === undefined ? (
                <Box aria-hidden="true" sx={{ width: 40, height: 40 }} />
              ) : (
                <Tooltip title={darkMode ? t("mode.light") : t("mode.dark")}>
                  <IconButton
                    aria-label={t("mode.toggle")}
                    aria-pressed={darkMode}
                    onClick={() => setMode(darkMode ? "light" : "dark")}
                    sx={{ color: "text.secondary" }}
                  >
                    {darkMode ? <LightModeRounded /> : <NightsStayRounded />}
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title={user ? t("auth.account") : t("auth.sign_in")}>
                <IconButton
                  aria-label={user ? t("auth.account") : t("auth.sign_in")}
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
                aria-label={t("upload.title")}
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
                  {t("upload.title")}
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
                  {smalls[view]}
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
                  label={t("workspace.page_count", { count: visibleDocuments.length })}
                  sx={{ alignSelf: { xs: "flex-start", sm: "flex-end" } }}
                />
              )}
            </Stack>
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
                    {view === "search" ? t("workspace.find_a_document") : t("workspace.quick_search")}
                  </Typography>
                  <Typography
                    variant="h5"
                    component="h2"
                    sx={{ my: 1, fontSize: 21 }}
                  >
                    {t("workspace.search_hero")}
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
                      alignItems: "center",
                    }}
                  >
                    <TextField
                      slotProps={{
                        htmlInput: { "aria-label": t("search.placeholder") },
                        input: {
                          startAdornment: (
                            <InputAdornment position="start">
                              <SearchRounded color="action" fontSize="small" />
                            </InputAdornment>
                          ),
                        },
                      }}
                      placeholder={t("search.placeholder")}
                      value={queryText}
                      onChange={(e) => setQueryText(e.target.value)}
                      fullWidth
                      sx={{ "& fieldset": { border: 0 } }}
                    />
                    <FormControlLabel
                      control={<Switch size="small" checked={fuzzy} onChange={(e) => setFuzzy(e.target.checked)} />}
                      label={t("search.fuzzy")}
                      sx={{ whiteSpace: "nowrap", mx: { xs: 0.5, sm: 1 } }}
                    />
                    <Button
                      type="submit"
                      variant="contained"
                      color="secondary"
                      disabled={searchLoading}
                      sx={{ width: { xs: "100%", sm: "auto" }, flexShrink: 0 }}
                    >
                      {searchLoading ? t("search.searching") : t("search.button")}
                    </Button>
                  </Paper>
                </Paper>
                {view === "library" && (
                  <Filters
                    draft={draft}
                    types={types}
                    teams={teams}
                    onChange={setDraft}
                    onApply={() => {
                      setFilters(draft);
                      void loadDocs(0, draft);
                    }}
                  />
                )}
                {view === "search" && searchLoading ? (
                  <Typography
                    color="text.secondary"
                    align="center"
                    sx={{ py: 10 }}
                  >
                    {t("common.loading_search")}
                  </Typography>
                ) : (
                  <DocumentGrid
                    items={gridItems}
                    loading={view === "library" && loading}
                    loadingLabel={t("common.loading_library")}
                    teams={teams}
                    emptyState={view === "search" ? "search" : "library"}
                    onDelete={removeDocument}
                    onOpen={openDocument}
                    onUpload={() => setUploadOpen(true)}
                  />
                )}
                {page.total_pages > 1 && (
                  <Pagination
                    page={page.page}
                    total={page.total_pages}
                    loading={loading || searchLoading}
                    onChange={(next) =>
                      searchReady
                        ? void search(null, next, submittedQuery)
                        : void loadDocs(next)
                    }
                  />
                )}
              </Box>
            ) : view === "people" ? (
              <People
                user={user}
                teams={teams}
                memberships={memberships}
                onTeams={setTeams}
                onMemberships={setMemberships}
              />
            ) : (
              <Settings
                user={user}
                onUser={setUser}
                types={types}
                onTypes={setTypes}
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
            void Promise.all([loadDocs(), loadMeta(u)]);
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
          types={types}
          writableTeams={writableTeams}
        />
      )}
      {selected && (
        <DocumentDetail
          document={selected}
          user={user}
          types={types}
          teams={teams}
          writableTeams={writableTeams}
          onClose={() => setSelected(null)}
          onSaved={(doc) => {
            setSelected(doc);
            setDocuments((items) =>
              items.map((item) => (item.id === doc.id ? doc : item)),
            );
            setSearchItems((items) =>
              items.map((item) => (item.document.id === doc.id ? { ...item, document: doc } : item)),
            );
          }}
          onDelete={removeDocument}
        />
      )}
      {documentToDelete !== null && (
        <Modal
          title={t("delete.title")}
          onClose={() => setDocumentToDelete(null)}
        >
          <Stack sx={{ gap: 2 }}>
            <Typography variant="body1">
              {t("delete.confirm")}
            </Typography>
          </Stack>
          <DialogActions sx={{ px: 0, mt: 3 }}>
            <Button
              type="button"
              variant="outlined"
              onClick={() => setDocumentToDelete(null)}
            >
              {t("common.cancel")}
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
                  setSearchItems((items) =>
                    items.filter((item) => item.document.id !== documentToDelete),
                  );
                  setSelected(null);
                  setDocumentToDelete(null);
                  toast("success", t("messages.document_deleted"));
                } catch (e) {
                  setDocumentToDelete(null);
                  toast("error", describeError(e, t));
                } finally {
                  setDeleting(false);
                }
              }}
            >
              {deleting ? t("delete.deleting") : t("common.delete")}
            </Button>
          </DialogActions>
        </Modal>
      )}
    </>
  );
}
