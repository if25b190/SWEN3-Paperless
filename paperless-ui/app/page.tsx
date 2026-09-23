"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, AppBar, Avatar, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Drawer, FormControl, IconButton, InputAdornment, InputLabel, List, ListItemButton, ListItemIcon, ListItemText, MenuItem, Paper, Select, Stack, TextField, Toolbar, Tooltip, Typography, useMediaQuery } from "@mui/material";
import { useColorScheme, useTheme } from "@mui/material/styles";
import AddRounded from "@mui/icons-material/AddRounded";
import CloseRounded from "@mui/icons-material/CloseRounded";
import DashboardRounded from "@mui/icons-material/DashboardRounded";
import DeleteOutlineRounded from "@mui/icons-material/DeleteOutlineRounded";
import DescriptionOutlined from "@mui/icons-material/DescriptionOutlined";
import DownloadRounded from "@mui/icons-material/DownloadRounded";
import Groups2Outlined from "@mui/icons-material/Groups2Outlined";
import Inventory2Outlined from "@mui/icons-material/Inventory2Outlined";
import LightModeRounded from "@mui/icons-material/LightModeRounded";
import MenuRounded from "@mui/icons-material/MenuRounded";
import NightsStayRounded from "@mui/icons-material/NightsStayRounded";
import PersonOutlineRounded from "@mui/icons-material/PersonOutlineRounded";
import PictureAsPdfOutlined from "@mui/icons-material/PictureAsPdfOutlined";
import SearchRounded from "@mui/icons-material/SearchRounded";
import SpaRounded from "@mui/icons-material/SpaRounded";
import TuneRounded from "@mui/icons-material/TuneRounded";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";
import { api, apiDelete, apiGetFile, apiGetFileBlob, apiUpdateDocument, apiUpload, type Correspondent, type Document, type DocumentType, type Member, type Team, type User } from "../lib/api";

type View = "library" | "search" | "people" | "settings";
type Notice = { tone: "error" | "success"; text: string } | null;
const initials = (name = "You") => name.split(/[ _-]/).map((part) => part[0]).join("").slice(0, 2).toUpperCase();
const bytes = (n: number) => n > 1_000_000 ? `${(n / 1_000_000).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1000))} KB`;
const previewMimeByExtension: Record<string, string> = { pdf: "application/pdf", png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp", avif: "image/avif", txt: "text/plain", md: "text/plain", markdown: "text/plain" };
function previewMime(document: Document): string | null {
  const extension = document.original_filename.toLowerCase().match(/\.([^.]+)$/)?.[1] || "";
  const safeMime = previewMimeByExtension[extension];
  if (!safeMime) return null;
  const declared = document.content_type.split(";")[0].trim().toLowerCase();
  if (declared === safeMime || declared === "application/octet-stream" || declared === "") return safeMime;
  if ((extension === "md" || extension === "markdown") && (declared === "text/markdown" || declared === "text/x-markdown")) return safeMime;
  return null;
}
type PreviewState = { key: string; stage: "loading" | "ready" | "error"; url?: string; text?: string; message?: string };
const sectionSx = { p: { xs: 2.5, sm: 3.5 }, minWidth: 0 };
const smallLabel = { fontSize: 11, fontWeight: 700, letterSpacing: ".18em", textTransform: "uppercase" as const };
const drawerWidth = 264;
const navigation = [
  { view: "library", label: "Library", icon: <DashboardRounded /> },
  { view: "search", label: "Search", icon: <SearchRounded /> },
  { view: "people", label: "People", icon: <Groups2Outlined /> },
  { view: "settings", label: "Settings", icon: <TuneRounded /> },
] as const;

function WorkspaceNavigation({ view, user, onSelect }: { view: View; user: User | null; onSelect: (view: View) => void }) {
  return <Box sx={{ display: "flex", flexDirection: "column", height: "100%", px: 2.5, py: 3.5, color: "#f3f8ee", position: "relative", zIndex: 1 }}>
    <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", mb: 6, px: 1 }}>
      <Avatar variant="rounded" sx={{ bgcolor: "#d8ef83", color: "#183729", width: 42, height: 42, borderRadius: 2 }}><SpaRounded /></Avatar>
      <Box><Typography sx={{ fontWeight: 700, letterSpacing: "-.055em", fontSize: 18, lineHeight: 1.2 }}>paperless<Box component="span" sx={{ color: "#d8ef83" }}>.</Box></Typography><Typography sx={{ fontSize: 10, letterSpacing: ".12em", color: "#bcd1c0" }}>DOCUMENT DESK</Typography></Box>
    </Stack>
    <Typography sx={{ ...smallLabel, fontSize: 10, color: "#bcd1c0", px: 2, mb: 1.25 }}>Browse</Typography>
    <List component="nav" aria-label="Main navigation" disablePadding>
      {navigation.map(({ view: key, label, icon }) => <ListItemButton component="button" type="button" key={key} selected={view === key} aria-current={view === key ? "page" : undefined} onClick={() => onSelect(key)} sx={{ width: "100%", borderRadius: 0, px: 2, py: 1.6, color: view === key ? "#e0f1aa" : "#c3d5c6", borderBottom: "1px solid #ffffff18", "&.Mui-selected, &.Mui-selected:hover": { bgcolor: "#ffffff0e", color: "#e0f1aa" }, "&:hover": { bgcolor: "#ffffff0b", color: "#fff" } }}><ListItemIcon sx={{ minWidth: 40, color: "inherit" }}>{icon}</ListItemIcon><ListItemText primary={label} slotProps={{ primary: { sx: { fontSize: 14, fontWeight: view === key ? 650 : 500 } } }} /></ListItemButton>)}
    </List>
    <Box sx={{ flex: 1 }} />
    <Divider sx={{ borderColor: "#ffffff2d", mb: 2.5 }} />
    <Stack direction="row" spacing={1.25} sx={{ alignItems: "center", px: 1 }}><Avatar sx={{ bgcolor: "#d8ef83", color: "#183729", width: 38, height: 38, fontSize: 13, fontWeight: 700 }}>{initials(user?.username)}</Avatar><Box sx={{ minWidth: 0 }}><Typography noWrap variant="body2" sx={{ fontWeight: 600 }}>{user?.username || "Guest reader"}</Typography><Typography noWrap sx={{ fontSize: 11, color: "#bcd1c0" }}>{user?.email || "Sign in to sync"}</Typography></Box></Stack>
  </Box>;
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return <Dialog open onClose={onClose} aria-labelledby="modal-title" fullWidth maxWidth="sm" scroll="paper" slotProps={{ paper: { sx: { p: { xs: 1, sm: 2 } } } }}>
    <DialogTitle id="modal-title" sx={{ display: "flex", justifyContent: "space-between", alignItems: "start", gap: 2, pb: 0 }}>
      <Box><Typography color="text.secondary" sx={smallLabel}>Paperless</Typography><Typography component="span" variant="h5" sx={{ display: "block", mt: 1 }}>{title}</Typography></Box>
      <IconButton aria-label={`Close ${title}`} onClick={onClose} size="small"><CloseRounded fontSize="small" /></IconButton>
    </DialogTitle>
    <DialogContent sx={{ pt: "16px !important" }}>{children}</DialogContent>
  </Dialog>;
}

export default function Home() {
  const [view, setView] = useState<View>("library"); const [user, setUser] = useState<User | null>(null); const [documents, setDocuments] = useState<Document[]>([]); const [selected, setSelected] = useState<Document | null>(null); const [page, setPage] = useState({ page: 0, total_pages: 0 }); const [loading, setLoading] = useState(false); const [notice, setNotice] = useState<Notice>(null); const [loginOpen, setLoginOpen] = useState(false); const [uploadOpen, setUploadOpen] = useState(false); const [queryText, setQueryText] = useState(""); const [submittedQuery, setSubmittedQuery] = useState(""); const [searchReady, setSearchReady] = useState(false); const [searchLoading, setSearchLoading] = useState(false); const [draft, setDraft] = useState({ correspondent_id: "", document_type_id: "" }); const [filters, setFilters] = useState(draft); const [correspondents, setCorrespondents] = useState<Correspondent[]>([]); const [types, setTypes] = useState<DocumentType[]>([]); const [teams, setTeams] = useState<Team[]>([]); const [users, setUsers] = useState<User[]>([]);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up("lg"));
  const { mode, setMode, systemMode } = useColorScheme();
  const darkMode = (mode === "system" ? systemMode : mode) === "dark";
  const fail = (error: unknown, fallback: string) => setNotice({ tone: "error", text: error instanceof Error ? error.message : fallback });
  const loadDocs = useCallback(async (next = 0, applied = filters) => { setLoading(true); try { const result = await api.documents({ page: next, size: 12, sort: "created_at,desc", ...applied }); setDocuments(result.items); setPage({ page: result.pagination.page, total_pages: result.pagination.total_pages }); } catch (e) { fail(e, "The library could not be loaded."); } finally { setLoading(false); } }, [filters]);
  const loadMeta = useCallback(async () => { try { const [c, t, team, people] = await Promise.all([api.labels("correspondents"), api.labels("document-types"), api.teams(), api.users()]); setCorrespondents(c.items as Correspondent[]); setTypes(t.items as DocumentType[]); setTeams(team.items); setUsers(people.items); } catch (e) { fail(e, "Workspace data could not be loaded."); } }, []);
  useEffect(() => { const boot = async () => { if (!localStorage.getItem("paperless_token")) return; try { const current = await api.me(); setUser(current); await Promise.all([loadDocs(), loadMeta()]); } catch (e) { fail(e, "Your session could not be restored."); } }; void boot(); const expired = () => { setUser(null); setDocuments([]); setSelected(null); setUploadOpen(false); setLoginOpen(true); }; window.addEventListener("paperless:unauthorized", expired); return () => window.removeEventListener("paperless:unauthorized", expired); }, [loadDocs, loadMeta]);
  const search = async (event: React.FormEvent, next = 0, query = queryText) => { event.preventDefault(); const submitted = query.trim(); if (!submitted) return; if (!next) setSubmittedQuery(submitted); setSearchLoading(true); setNotice(null); try { const result = await api.search({ query: submitted, fuzzy: true, page: next, size: 12 }); setDocuments(result.items.map((item) => item.document)); setPage({ page: result.pagination.page, total_pages: result.pagination.total_pages }); setSearchReady(true); setView("search"); } catch (e) { fail(e, "Search results could not be loaded."); } finally { setSearchLoading(false); } };
  const openDocument = (doc: Document) => api.document(doc.id).then(setSelected).catch((e) => fail(e, "Document details could not be loaded."));
  const removeDocument = async (id: number) => { if (!confirm("Delete this document?")) return; try { await apiDelete(`/documents/${id}`); setDocuments((items) => items.filter((item) => item.id !== id)); setSelected(null); } catch (e) { fail(e, "Document could not be deleted."); } };
  const selectView = (next: View) => { setMobileNavOpen(false); setView(next); if (next === "search") { setQueryText(""); setSubmittedQuery(""); setDocuments([]); setSearchReady(true); setPage({ page: 0, total_pages: 0 }); } if (next === "library") { setSearchReady(false); void loadDocs(); } };
  const headings = { library: "Good documents, within reach.", search: submittedQuery ? `Results for “${submittedQuery}”` : "Search your archive", people: "Your people", settings: "Workspace settings" };
  const captions = { library: "Everything you need to keep your documents in order.", search: "Find the right file without digging through folders.", people: "Manage the people and teams in your workspace.", settings: "Keep your document labels organized." };

  return <>
    <Box id="workspace-shell" component="main" sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
      <Drawer variant={desktop ? "permanent" : "temporary"} open={desktop || mobileNavOpen} onClose={() => setMobileNavOpen(false)} slotProps={{ paper: { className: "workspace-sidebar", sx: { width: drawerWidth, border: 0, backgroundImage: "none" } } }}>
        <WorkspaceNavigation view={view} user={user} onSelect={selectView} />
      </Drawer>
      <Box sx={{ ml: { lg: `${drawerWidth}px` }, minWidth: 0 }}>
        <AppBar position="sticky" color="inherit" elevation={0} sx={{ bgcolor: "background.paper", borderBottom: "1px solid", borderColor: "divider", backgroundImage: "none" }}>
          <Toolbar sx={{ minHeight: { xs: 64, sm: 72 }, px: { xs: 2, sm: 4, lg: 6 }, gap: { xs: 1, sm: 2 } }}>
            {!desktop && <IconButton edge="start" aria-label="Open navigation" aria-expanded={mobileNavOpen} onClick={() => setMobileNavOpen(true)} sx={{ color: "text.primary" }}><MenuRounded /></IconButton>}
            <Typography sx={{ fontSize: { xs: 16, sm: 18 }, fontWeight: 650, letterSpacing: "-.025em", whiteSpace: "nowrap" }}>{navigation.find((item) => item.view === view)?.label}</Typography>
            <Box sx={{ flex: 1 }} />
            {mode === undefined ? <Box aria-hidden="true" sx={{ width: 40, height: 40 }} /> : <Tooltip title={darkMode ? "Use light mode" : "Use dark mode"}><IconButton aria-label="Dark mode" aria-pressed={darkMode} onClick={() => setMode(darkMode ? "light" : "dark")} sx={{ color: "text.secondary" }}>{darkMode ? <LightModeRounded /> : <NightsStayRounded />}</IconButton></Tooltip>}
            <Tooltip title={user ? "Account" : "Sign in"}><IconButton aria-label={user ? "Account" : "Sign in"} onClick={() => setLoginOpen(true)} sx={{ color: "text.secondary" }}><PersonOutlineRounded /></IconButton></Tooltip>
            <Divider orientation="vertical" flexItem sx={{ mx: { xs: .5, sm: 1 }, my: 1.5 }} />
            <Button variant="contained" color="secondary" startIcon={<AddRounded />} onClick={() => setUploadOpen(true)} aria-label="Upload document" sx={{ minWidth: { xs: 42, sm: 0 }, width: { xs: 42, sm: "auto" }, height: 42, px: { xs: 0, sm: 2 }, whiteSpace: "nowrap", "& .MuiButton-startIcon": { m: { xs: 0, sm: "0 8px 0 0" } } }}><Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>Upload document</Box></Button>
          </Toolbar>
        </AppBar>
        <Box sx={{ maxWidth: 1400, mx: "auto", px: { xs: 2.5, sm: 5, lg: 6 }, py: { xs: 4, lg: 6 } }}>
          <Stack className="reveal" direction={{ xs: "column", sm: "row" }} sx={{ gap: 3,  alignItems: { sm: "flex-end" }, justifyContent: "space-between",  mb: 4 }}>
            <Box><Typography color="primary.main" sx={{ ...smallLabel, mb: 1.5, letterSpacing: ".12em" }}>{view === "library" ? "Your documents" : view === "people" ? "People and teams" : view === "settings" ? "Manage your workspace" : "Find documents"}</Typography><Typography component="h1" variant="h1" sx={{ fontSize: { xs: 32, sm: 42, lg: 48 }, lineHeight: 1.18, maxWidth: 750 }}>{headings[view]}</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: 1.5 }}>{captions[view]}</Typography></Box>
            {view === "library" && <Chip variant="outlined" label={`${documents.length} documents on this page`} sx={{ alignSelf: { xs: "flex-start", sm: "flex-end" } }} />}
          </Stack>
          {notice && <Alert severity={notice.tone} role="alert" sx={{ mb: 3 }}>{notice.text}</Alert>}
          {view === "library" || view === "search" ? <Box className="reveal reveal-later">
            <Paper className="search-hero" elevation={0} sx={{ border: "1px solid", borderColor: "divider", borderRadius: 4, p: { xs: 2.5, sm: 4 }, mb: 3.5 }}>
              <Typography color="text.secondary" sx={smallLabel}>{view === "search" ? "Find a document" : "Quick search"}</Typography><Typography variant="h5" component="h2" sx={{ my: 1, fontSize: 21 }}>Your files, a little easier to find.</Typography>
              <Paper component="form" onSubmit={(e) => search(e)} elevation={0} sx={{ mt: 2.5, p: 1, border: "1px solid", borderColor: "divider", borderRadius: 2, display: "flex", flexWrap: { xs: "wrap", sm: "nowrap" }, gap: 1 }}><TextField slotProps={{ htmlInput: { "aria-label": "Search documents" }, input: { startAdornment: <InputAdornment position="start"><SearchRounded color="action" fontSize="small" /></InputAdornment> } }} placeholder="Search by title, text, or filename" value={queryText} onChange={(e) => setQueryText(e.target.value)} fullWidth sx={{ "& fieldset": { border: 0 } }} /><Button type="submit" variant="contained" color="secondary" disabled={searchLoading} sx={{ width: { xs: "100%", sm: "auto" }, flexShrink: 0 }}>{searchLoading ? "Searching…" : "Search"}</Button></Paper>
            </Paper>
            {view === "library" && <Filters draft={draft} correspondents={correspondents} types={types} onChange={setDraft} onApply={() => { setFilters(draft); setSearchReady(false); void loadDocs(0, draft); }} />}
            {searchLoading ? <Typography color="text.secondary" align="center" sx={{ py: 10 }}>Loading search results…</Typography> : <DocumentGrid documents={documents} loading={loading} onDelete={removeDocument} onOpen={openDocument} onUpload={() => setUploadOpen(true)} ready={searchReady} />}
            {page.total_pages > 1 && <Pagination page={page.page} total={page.total_pages} loading={loading || searchLoading} onChange={(next) => searchReady ? void search(new Event("submit") as unknown as React.FormEvent, next, submittedQuery) : void loadDocs(next)} />}
          </Box> : view === "people" ? <People users={users} teams={teams} onUsers={setUsers} onTeams={setTeams} onError={fail} /> : <Settings correspondents={correspondents} types={types} onChange={(kind, items) => kind === "correspondents" ? setCorrespondents(items as Correspondent[]) : setTypes(items as DocumentType[])} onError={fail} />}
        </Box>
      </Box>
    </Box>
    {loginOpen && <LoginDialog onClose={() => setLoginOpen(false)} onLogin={(u) => { setUser(u); setLoginOpen(false); void Promise.all([loadDocs(), loadMeta()]); }} onError={fail} />}
    {uploadOpen && <UploadDialog onClose={() => setUploadOpen(false)} onUploaded={(doc) => { setDocuments((items) => [doc, ...items]); setUploadOpen(false); }} onError={fail} />}
    {selected && <DocumentDetail document={selected} correspondents={correspondents} types={types} onClose={() => setSelected(null)} onSaved={(doc) => { setSelected(doc); setDocuments((items) => items.map((item) => item.id === doc.id ? doc : item)); }} onDelete={removeDocument} onError={fail} />}
  </>;
}

function Filters({ draft, correspondents, types, onChange, onApply }: { draft: { correspondent_id: string; document_type_id: string }; correspondents: Correspondent[]; types: DocumentType[]; onChange: (v: { correspondent_id: string; document_type_id: string }) => void; onApply: () => void }) {
  return <Stack direction="row" sx={{ gap: 1.5, alignItems: "center", flexWrap: "wrap", mb: 3 }}>
    <Typography color="text.secondary" sx={{ ...smallLabel, mr: 1 }}>Filter by</Typography>
    <FormControl size="small" sx={{ minWidth: 190 }}><InputLabel id="filter-correspondent-label" shrink>Correspondent</InputLabel><Select labelId="filter-correspondent-label" label="Correspondent" displayEmpty value={draft.correspondent_id} onChange={(e) => onChange({ ...draft, correspondent_id: e.target.value })}><MenuItem value="">All correspondents</MenuItem>{correspondents.map((x) => <MenuItem key={x.id} value={String(x.id)}>{x.name}</MenuItem>)}</Select></FormControl>
    <FormControl size="small" sx={{ minWidth: 170 }}><InputLabel id="filter-document-type-label" shrink>Document type</InputLabel><Select labelId="filter-document-type-label" label="Document type" displayEmpty value={draft.document_type_id} onChange={(e) => onChange({ ...draft, document_type_id: e.target.value })}><MenuItem value="">All types</MenuItem>{types.map((x) => <MenuItem key={x.id} value={String(x.id)}>{x.name}</MenuItem>)}</Select></FormControl>
    <Button variant="outlined" onClick={onApply}>Apply filters</Button>
  </Stack>;
}

function Pagination({ page, total, loading, onChange }: { page: number; total: number; loading: boolean; onChange: (page: number) => void }) {
  return <Stack direction="row" sx={{ gap: 1,  alignItems: "center", justifyContent: "flex-end", flexWrap: "wrap",  mt: 4 }}><Button variant="outlined" disabled={loading || !page} onClick={() => onChange(page - 1)}>Previous</Button><Typography color="text.secondary" variant="body2" sx={{ px: 1 }}>Page {page + 1} of {total}</Typography><Button variant="outlined" disabled={loading || page + 1 >= total} onClick={() => onChange(page + 1)}>Next</Button></Stack>;
}

function DocumentGrid({ documents, loading, onDelete, onOpen, onUpload, ready }: { documents: Document[]; loading: boolean; onDelete: (id: number) => void; onOpen: (doc: Document) => void; onUpload: () => void; ready: boolean }) {
  if (loading) return <Typography color="text.secondary" align="center" sx={{ py: 10 }}>Loading your library…</Typography>;
  if (!documents.length) return <Card variant="outlined" sx={{ textAlign: "center", borderStyle: "dashed", boxShadow: "none" }}><CardContent sx={{ py: "60px !important", px: 3 }}><Avatar variant="rounded" sx={{ mx: "auto", mb: 2.5, width: 62, height: 62, bgcolor: "secondary.main", color: "secondary.contrastText" }}><Inventory2Outlined fontSize="large" /></Avatar><Typography component="h2" variant="h5">{ready ? "Search your archive" : "Your desk is ready"}</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: 1, mb: 3 }}>{ready ? "Enter a phrase above to find a document." : "Upload a document to start building your library."}</Typography><Button variant="contained" color="secondary" startIcon={<AddRounded />} onClick={onUpload}>Upload document</Button></CardContent></Card>;
  return <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "repeat(2,minmax(0,1fr))", xl: "repeat(3,minmax(0,1fr))" } }}>{documents.map((doc) => <Card key={doc.id} component="article" sx={{ display: "flex", flexDirection: "column", minHeight: 265, transition: "transform .25s, box-shadow .25s", "&:hover": { transform: "translateY(-4px)", boxShadow: 6 } }}><CardContent sx={{ display: "flex", flexDirection: "column", flex: 1, p: 3 }}><Button onClick={() => onOpen(doc)} sx={{ textAlign: "left", p: 0, flex: 1, display: "block", color: "text.primary", "&:hover": { bgcolor: "transparent", textDecoration: "underline", textDecorationColor: "secondary.main", textUnderlineOffset: 4 } }}><Stack direction="row" sx={{ alignItems: "start", justifyContent: "space-between", mb: 3.5 }}><Avatar variant="rounded" sx={{ bgcolor: "action.hover", color: "text.primary" }}>{doc.content_type.includes("pdf") ? <PictureAsPdfOutlined /> : <DescriptionOutlined />}</Avatar><Chip size="small" label={doc.status.replaceAll("_", " ")} sx={{ bgcolor: "action.hover", fontSize: 10, fontWeight: 700 }} /></Stack><Typography component="h2" variant="h6" sx={{ fontWeight: 650, lineHeight: 1.3, display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{doc.title}</Typography><Typography color="text.secondary" variant="caption" noWrap sx={{ display: "block", mt: 1.5 }}>{doc.original_filename} · {bytes(doc.file_size)}</Typography></Button><Divider sx={{ my: 2 }} /><Stack direction="row" sx={{ gap: 1, alignItems: "center", justifyContent: "space-between" }}><Typography color="text.secondary" variant="caption" noWrap>{doc.correspondent?.name || "Unassigned"}</Typography><Button size="small" color="error" startIcon={<DeleteOutlineRounded fontSize="small" />} aria-label={`Delete ${doc.title}`} onClick={() => onDelete(doc.id)}>Delete</Button></Stack></CardContent></Card>)}</Box>;
}

function LoginDialog({ onClose, onLogin, onError }: { onClose: () => void; onLogin: (u: User) => void; onError: (e: unknown, f: string) => void }) {
  const [username, setUsername] = useState(""); const [password, setPassword] = useState(""); const [error, setError] = useState("");
  const submit = async (e: React.FormEvent) => { e.preventDefault(); try { const result = await api.login({ username, password }); localStorage.setItem("paperless_token", result.token); onLogin(result.user); } catch (err) { setError(err instanceof Error ? err.message : "Sign in failed."); onError(err, "Sign in failed."); } };
  return <Modal title="Welcome back." onClose={onClose}><Box component="form" onSubmit={submit}><Stack sx={{ gap: 2,  mt: 1 }}><TextField label="Username" required autoFocus value={username} onChange={(e) => setUsername(e.target.value)} /><TextField label="Password" required type="password" value={password} onChange={(e) => setPassword(e.target.value)} />{error && <Alert severity="error" role="alert">{error}</Alert>}</Stack><DialogActions sx={{ px: 0, mt: 3 }}><Button type="button" variant="outlined" onClick={onClose}>Cancel</Button><Button type="submit" variant="contained">Sign in</Button></DialogActions></Box></Modal>;
}

function UploadDialog({ onClose, onUploaded, onError }: { onClose: () => void; onUploaded: (d: Document) => void; onError: (e: unknown, f: string) => void }) {
  const [title, setTitle] = useState(""); const [file, setFile] = useState<File>();
  const submit = async (e: React.FormEvent) => { e.preventDefault(); if (!file || !title) return; try { onUploaded(await apiUpload<Document>("/documents", { document: file, title })); } catch (err) { onError(err, "Upload failed."); } };
  return <Modal title="Upload document" onClose={onClose}><Box component="form" onSubmit={submit}><Stack sx={{ gap: 2, mt: 1 }}><TextField label="Title" required autoFocus value={title} onChange={(e) => setTitle(e.target.value)} /><TextField label="File" type="file" required slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => setFile((e.target as HTMLInputElement).files?.[0])} /></Stack><DialogActions sx={{ px: 0, mt: 3 }}><Button type="submit" variant="contained">Upload document</Button></DialogActions></Box></Modal>;
}

function DocumentDetail({ document, correspondents, types, onClose, onSaved, onDelete, onError }: { document: Document; correspondents: Correspondent[]; types: DocumentType[]; onClose: () => void; onSaved: (d: Document) => void; onDelete: (id: number) => void; onError: (e: unknown, f: string) => void }) {
  const [title, setTitle] = useState(document.title); const [correspondentId, setCorrespondentId] = useState(String(document.correspondent?.id || "")); const [typeId, setTypeId] = useState(String(document.document_type?.id || ""));
  const [preview, setPreview] = useState<PreviewState | null>(null);
  const requestId = useRef(0);
  const previewUrl = useRef<string | null>(null);
  const safeMime = previewMime(document);
  const previewKey = `${document.id}:${document.original_filename}:${document.content_type}`;
  useEffect(() => () => { requestId.current++; if (previewUrl.current) URL.revokeObjectURL(previewUrl.current); previewUrl.current = null; }, [previewKey]);
  const loadPreview = async () => {
    if (!safeMime) return;
    const request = ++requestId.current;
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = null;
    setPreview({ key: previewKey, stage: "loading" });
    try {
      const blob = await apiGetFileBlob(document.id);
      if (request !== requestId.current) return;
      if (safeMime === "text/plain") {
        const text = await blob.text();
        if (request === requestId.current) setPreview({ key: previewKey, stage: "ready", text });
      } else {
        // The download response may be generic; only use the allowlisted metadata type.
        const url = URL.createObjectURL(new Blob([blob], { type: safeMime }));
        if (request !== requestId.current) URL.revokeObjectURL(url);
        else { previewUrl.current = url; setPreview({ key: previewKey, stage: "ready", url }); }
      }
    } catch (error) {
      if (request === requestId.current) setPreview({ key: previewKey, stage: "error", message: error instanceof Error ? error.message : "Preview could not be loaded." });
    }
  };
  const save = async (e: React.FormEvent) => { e.preventDefault(); try { await Promise.all([correspondentId ? api.correspondent(Number(correspondentId)) : Promise.resolve(), typeId ? api.documentType(Number(typeId)) : Promise.resolve()]); onSaved(await apiUpdateDocument(document.id, { title, correspondent_id: correspondentId ? Number(correspondentId) : null, document_type_id: typeId ? Number(typeId) : null })); } catch (err) { onError(err, "Document could not be updated."); } };
  const visiblePreview = preview?.key === previewKey ? preview : null;
  return <Modal title="Document details" onClose={onClose}>
    <Typography color="text.secondary" variant="body2" sx={{ mb: 2 }}>{document.original_filename} · {bytes(document.file_size)}</Typography>
    <Stack direction="row" sx={{ gap: 1, flexWrap: "wrap", mb: 2.5 }}>
      {safeMime && <Button type="button" variant="contained" color="secondary" startIcon={<VisibilityOutlined />} disabled={visiblePreview?.stage === "loading"} onClick={() => void loadPreview()}>Preview</Button>}
      <Button type="button" variant="outlined" startIcon={<DownloadRounded fontSize="small" />} onClick={() => apiGetFile(document.id, document.original_filename).catch((e) => onError(e, "Download failed."))}>Download</Button>
    </Stack>
    {!safeMime && <Alert severity="info" sx={{ mb: 3 }}>Preview is unavailable for this file type. Download it to open it.</Alert>}
    {visiblePreview && <Paper variant="outlined" sx={{ p: 2, mb: 3, bgcolor: "background.default", minWidth: 0 }}>
      {visiblePreview.stage === "loading" && <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}><CircularProgress size={20} aria-label="Loading preview" /><Typography variant="body2">Loading preview…</Typography></Stack>}
      {visiblePreview.stage === "error" && <Alert severity="error">{visiblePreview.message}</Alert>}
      {visiblePreview.stage === "ready" && safeMime === "text/plain" && <Typography component="pre" variant="body2" sx={{ m: 0, whiteSpace: "pre-wrap", overflowWrap: "anywhere", maxHeight: 440, overflow: "auto" }}>{visiblePreview.text}</Typography>}
      {visiblePreview.stage === "ready" && safeMime?.startsWith("image/") && <Box component="img" src={visiblePreview.url} alt={`Preview of ${document.title}`} sx={{ display: "block", maxWidth: "100%", maxHeight: 440, objectFit: "contain", mx: "auto" }} />}
      {visiblePreview.stage === "ready" && safeMime === "application/pdf" && <Box component="iframe" title={`Preview of ${document.title}`} src={visiblePreview.url} sandbox="allow-scripts" sx={{ display: "block", border: 0, width: "100%", height: 440, bgcolor: "background.paper" }} />}
    </Paper>}
    <Box component="form" onSubmit={save}><Stack sx={{ gap: 2 }}>
      <TextField label="Title" autoFocus value={title} onChange={(e) => setTitle(e.target.value)} />
      <FormControl size="small" fullWidth><InputLabel id="document-correspondent-label" shrink>Document correspondent</InputLabel><Select labelId="document-correspondent-label" label="Document correspondent" displayEmpty value={correspondentId} onChange={(e) => setCorrespondentId(e.target.value)}><MenuItem value="">None</MenuItem>{correspondents.map((x) => <MenuItem key={x.id} value={String(x.id)}>{x.name}</MenuItem>)}</Select></FormControl>
      <FormControl size="small" fullWidth><InputLabel id="document-type-label" shrink>Document type</InputLabel><Select labelId="document-type-label" label="Document type" displayEmpty value={typeId} onChange={(e) => setTypeId(e.target.value)}><MenuItem value="">None</MenuItem>{types.map((x) => <MenuItem key={x.id} value={String(x.id)}>{x.name}</MenuItem>)}</Select></FormControl>
    </Stack><DialogActions sx={{ px: 0, mt: 3, flexWrap: "wrap" }}><Button type="button" variant="outlined" color="error" startIcon={<DeleteOutlineRounded fontSize="small" />} onClick={() => onDelete(document.id)} sx={{ mr: "auto" }}>Delete</Button><Button type="submit" variant="contained">Save changes</Button></DialogActions></Box>
  </Modal>;
}

function People({ users, teams, onUsers, onTeams, onError }: { users: User[]; teams: Team[]; onUsers: (v: User[]) => void; onTeams: (v: Team[]) => void; onError: (e: unknown, f: string) => void }) {
  const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [teamName, setTeamName] = useState(""); const [members, setMembers] = useState<Record<number, Member[]>>({}); const [memberId, setMemberId] = useState<Record<number, string>>({}); const [roles, setRoles] = useState<Record<number, Member["role"]>>({});
  const createUser = async (e: React.FormEvent) => { e.preventDefault(); try { onUsers([...users, await api.createUser({ username: name, email, password })]); setName(""); setEmail(""); setPassword(""); } catch (err) { onError(err, "User could not be created."); } };
  const createTeam = async (e: React.FormEvent) => { e.preventDefault(); try { onTeams([...teams, await api.createTeam({ name: teamName })]); setTeamName(""); } catch (err) { onError(err, "Team could not be created."); } };
  const loadMembers = async (id: number) => { try { const result = await api.members(id); setMembers((current) => ({ ...current, [id]: result.items })); } catch (err) { onError(err, "Members could not be loaded."); } };
  const addMember = async (id: number) => { const userId = memberId[id]; if (!userId) return; try { const member = await api.addMember(id, { user_id: Number(userId), role: roles[id] || "MEMBER" }); setMembers((current) => ({ ...current, [id]: [...(current[id] || []), member] })); setMemberId((current) => ({ ...current, [id]: "" })); } catch (err) { onError(err, "Member could not be added."); } };
  const updateMember = async (teamId: number, userId: number, nextRole: Member["role"]) => { try { const updated = await api.updateMember(teamId, userId, nextRole); setMembers((current) => ({ ...current, [teamId]: (current[teamId] || []).map((member) => member.user.id === userId ? updated : member) })); } catch (err) { onError(err, "Role could not be updated."); } };
  const removeMember = async (teamId: number, userId: number) => { try { await api.removeMember(teamId, userId); setMembers((current) => ({ ...current, [teamId]: (current[teamId] || []).filter((member) => member.user.id !== userId) })); } catch (err) { onError(err, "Member could not be removed."); } };
  return <Box className="reveal" sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "repeat(2,minmax(0,1fr))" }, gap: 3 }}>
    <Card component="section" sx={sectionSx}><Typography component="h2" variant="h5">Members</Typography><Stack component="form" onSubmit={createUser} sx={{ gap: 1.5,  mt: 3 }}><TextField label="Username" required value={name} onChange={(e) => setName(e.target.value)} /><TextField label="Email" required type="email" value={email} onChange={(e) => setEmail(e.target.value)} /><TextField label="Password" required slotProps={{ htmlInput: { minLength: 6 } }} type="password" value={password} onChange={(e) => setPassword(e.target.value)} /><Button type="submit" variant="contained" sx={{ alignSelf: "flex-start" }}>Add user</Button></Stack><Stack sx={{ gap: 1,  mt: 3 }}>{users.map((u) => <Paper key={u.id} variant="outlined" sx={{ p: 1.5, display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 1, bgcolor: "action.hover" }}><Typography variant="body2" sx={{ fontWeight: 600 }}>{u.username}</Typography><Stack direction="row" sx={{ flexWrap: "wrap" }}><Button size="small" onClick={() => api.user(u.id).then((detail) => { const username = prompt("Username", detail.username); if (username) return api.updateUser(u.id, { username }).then((next) => onUsers(users.map((x) => x.id === next.id ? next : x))); return undefined; }).catch((err) => onError(err, "User could not be edited."))}>Edit</Button><Button size="small" onClick={() => api.userTeams(u.id).then((result) => alert(result.items.map((x) => `${x.team.name}: ${x.role}`).join("\n") || "No memberships")).catch((err) => onError(err, "Memberships could not be loaded."))}>Memberships</Button><Button size="small" color="error" onClick={() => api.deleteUser(u.id).then(() => onUsers(users.filter((x) => x.id !== u.id))).catch((err) => onError(err, "User could not be deleted."))}>Delete</Button></Stack></Paper>)}</Stack></Card>
    <Card component="section" sx={sectionSx}><Typography component="h2" variant="h5">Teams</Typography><Stack component="form" onSubmit={createTeam} direction="row" sx={{ gap: 1,  flexWrap: "wrap",  mt: 3 }}><TextField label="Team name" required value={teamName} onChange={(e) => setTeamName(e.target.value)} sx={{ flex: 1, minWidth: 160 }} /><Button type="submit" variant="contained">Add team</Button></Stack><Stack sx={{ gap: 1,  mt: 3 }}>{teams.map((team) => <Paper key={team.id} data-team-id={team.id} variant="outlined" sx={{ p: 2, bgcolor: "action.hover" }}><Stack direction="row" sx={{ gap: 1,  alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}><Typography sx={{ fontWeight: 600 }}>{team.name}</Typography><Stack direction="row" sx={{ flexWrap: "wrap" }}><Button size="small" onClick={() => api.team(team.id).then((detail) => { const renamed = prompt("Team name", detail.name); if (renamed) return api.updateTeam(team.id, { name: renamed }).then((next) => onTeams(teams.map((x) => x.id === next.id ? next : x))); return undefined; }).catch((err) => onError(err, "Team could not be edited."))}>Edit</Button><Button size="small" onClick={() => void loadMembers(team.id)}>Members</Button><Button size="small" color="error" onClick={() => api.deleteTeam(team.id).then(() => onTeams(teams.filter((x) => x.id !== team.id))).catch((err) => onError(err, "Team could not be deleted."))}>Delete</Button></Stack></Stack>
      {members[team.id] && <Stack sx={{ gap: 1.5, mt: 2, pt: 2, borderTop: "1px solid", borderColor: "divider" }}>
        {members[team.id].map((member) => <Stack key={member.user.id} direction="row" sx={{ gap: 1, alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
          <Typography variant="body2">{member.user.username}</Typography>
          <Stack direction="row" sx={{ gap: 1, alignItems: "center" }}>
            <FormControl size="small" sx={{ minWidth: 165 }}><InputLabel id={`member-role-${team.id}-${member.user.id}`} shrink>{`Role for ${member.user.username}`}</InputLabel><Select labelId={`member-role-${team.id}-${member.user.id}`} label={`Role for ${member.user.username}`} value={member.role} onChange={(e) => void updateMember(team.id, member.user.id, e.target.value as Member["role"])}><MenuItem value="ADMIN">ADMIN</MenuItem><MenuItem value="READONLY">READONLY</MenuItem><MenuItem value="MEMBER">MEMBER</MenuItem></Select></FormControl>
            <Button size="small" color="error" onClick={() => void removeMember(team.id, member.user.id)}>Remove</Button>
          </Stack>
        </Stack>)}
        <Stack direction="row" sx={{ gap: 1, alignItems: "center", flexWrap: "wrap" }}>
          <FormControl size="small" sx={{ minWidth: 180, flex: 1 }}><InputLabel id={`member-user-${team.id}`} shrink>{`Member user for ${team.name}`}</InputLabel><Select labelId={`member-user-${team.id}`} label={`Member user for ${team.name}`} displayEmpty value={memberId[team.id] || ""} onChange={(e) => setMemberId((current) => ({ ...current, [team.id]: e.target.value }))}><MenuItem value="">Add user…</MenuItem>{users.map((u) => <MenuItem key={u.id} value={String(u.id)}>{u.username}</MenuItem>)}</Select></FormControl>
          <FormControl size="small" sx={{ minWidth: 190, flex: 1 }}><InputLabel id={`new-member-role-${team.id}`} shrink>{`New member role for ${team.name}`}</InputLabel><Select labelId={`new-member-role-${team.id}`} label={`New member role for ${team.name}`} value={roles[team.id] || "MEMBER"} onChange={(e) => setRoles((current) => ({ ...current, [team.id]: e.target.value as Member["role"] }))}><MenuItem value="ADMIN">ADMIN</MenuItem><MenuItem value="READONLY">READONLY</MenuItem><MenuItem value="MEMBER">MEMBER</MenuItem></Select></FormControl>
          <Button variant="contained" color="secondary" size="small" disabled={!memberId[team.id]} onClick={() => void addMember(team.id)}>Add</Button>
        </Stack>
      </Stack>}
    </Paper>)}</Stack></Card>
  </Box>;
}

function Settings({ correspondents, types, onChange, onError }: { correspondents: Correspondent[]; types: DocumentType[]; onChange: (kind: "correspondents" | "document-types", items: (Correspondent | DocumentType)[]) => void; onError: (e: unknown, f: string) => void }) {
  const [name, setName] = useState(""); const [description, setDescription] = useState("");
  const add = async (kind: "correspondents" | "document-types") => { try { const item = kind === "correspondents" ? await api.createLabel(kind, { name, notes: description }) : await api.createDocumentType({ name, description }); onChange(kind, [...(kind === "correspondents" ? correspondents : types), item]); setName(""); setDescription(""); } catch (err) { onError(err, "Item could not be created."); } };
  const row = (kind: "correspondents" | "document-types", item: Correspondent | DocumentType) => { const text = kind === "correspondents" ? (item as Correspondent).notes : (item as DocumentType).description; const edit = async () => { try { const detail = kind === "correspondents" ? await api.correspondent(item.id) : await api.documentType(item.id); const next = prompt("Name", detail.name); const detailText = kind === "correspondents" ? (detail as Correspondent).notes : (detail as DocumentType).description; const value = prompt("Description", detailText || ""); if (!next) return; if (kind === "correspondents") { const updated = await api.updateLabel(kind, item.id, { name: next, notes: value || undefined }); onChange(kind, correspondents.map((v) => v.id === updated.id ? updated : v)); } else { const updated = await api.updateDocumentType(item.id, { name: next, description: value || undefined }); onChange(kind, types.map((v) => v.id === updated.id ? updated : v)); } } catch (err) { onError(err, "Item could not be edited."); } };
    return <Paper key={item.id} variant="outlined" sx={{ p: 1.5, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1, flexWrap: "wrap", bgcolor: "action.hover" }}><Box><Typography variant="body2" sx={{ fontWeight: 600 }}>{item.name}</Typography><Typography variant="caption" color="text.secondary">{text || "No description"}</Typography></Box><Stack direction="row"><Button size="small" onClick={() => void edit()}>Edit</Button><Button size="small" color="error" onClick={() => api.deleteLabel(kind, item.id).then(() => onChange(kind, (kind === "correspondents" ? correspondents : types).filter((x) => x.id !== item.id))).catch((err) => onError(err, "Item could not be deleted."))}>Delete</Button></Stack></Paper>; };
  return <Stack className="reveal" sx={{ gap: 2.5,  maxWidth: 920 }}><Card component="section" sx={sectionSx}><Typography component="h2" variant="h5">Correspondents and document types</Typography><Stack direction={{ xs: "column", md: "row" }} sx={{ gap: 1.5,  mt: 3 }}><TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} sx={{ flex: 1 }} slotProps={{ htmlInput: { "aria-label": "Metadata name" } }} /><TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} sx={{ flex: 1 }} slotProps={{ htmlInput: { "aria-label": "Metadata description" } }} /><Stack direction="row" sx={{ gap: 1,  flexWrap: "wrap" }}><Button onClick={() => void add("correspondents")} variant="contained">Add correspondent</Button><Button onClick={() => void add("document-types")} variant="contained" color="secondary">Add type</Button></Stack></Stack></Card><Card component="section" sx={sectionSx}><Typography component="h2" variant="h5">Correspondents</Typography><Stack sx={{ gap: 1,  mt: 2 }}>{correspondents.map((x) => row("correspondents", x))}</Stack></Card><Card component="section" sx={sectionSx}><Typography component="h2" variant="h5">Document types</Typography><Stack sx={{ gap: 1,  mt: 2 }}>{types.map((x) => row("document-types", x))}</Stack></Card></Stack>;
}
