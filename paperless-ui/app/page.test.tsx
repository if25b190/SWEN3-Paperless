import { act, render as rtlRender, screen, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import Home from "./page";
import { I18nProvider } from "../lib/i18n/I18nProvider";
import { ToastProvider } from "../lib/toast/ToastProvider";
import { theme } from "./providers";

Object.defineProperty(window, "matchMedia", { writable: true, value: (query: string) => ({ matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn() }) });
const render = (ui: React.ReactNode) =>
  rtlRender(
    <ThemeProvider theme={theme} defaultMode="system" modeStorageKey="paperless_theme">
      <I18nProvider>
        <ToastProvider>{ui}</ToastProvider>
      </I18nProvider>
    </ThemeProvider>,
  );

describe("Paperless workspace", () => {
  const json = (body: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, statusText: "Request failed", json: async () => body });
  beforeEach(() => { localStorage.clear(); document.documentElement.classList.remove("dark"); });
  const openNavigation = async (user: UserEvent) => {
    await user.click(screen.getByRole("button", { name: "Open navigation" }));
    return screen.getByRole("navigation", { name: "Main navigation" });
  };
  const choose = async (user: UserEvent, field: HTMLElement, option: string) => {
    await user.click(field);
    await user.click(await screen.findByRole("option", { name: option }));
  };
  const sampleDocument = (filename = "sample.png", contentType = "image/png") => ({ id: 8, title: "Sample document", owner_id: 1, original_filename: filename, content_type: contentType, file_size: 128, status: "COMPLETED", created_at: "today" });
  const mockWorkspace = (document = sampleDocument(), content = "# Notes\n<script>unsafe</script>") => {
    localStorage.setItem("paperless_token", "token");
    const blob = new Blob([content], { type: document.content_type });
    Object.defineProperty(blob, "text", { value: async () => content });
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [document], pagination: { page: 0, size: 12, total_elements: 1, total_pages: 1 } }));
      if (url.endsWith("/documents/8/download")) return Promise.resolve({ ok: true, status: 200, blob: async () => blob });
      if (url.endsWith("/documents/8") && options?.method === "PUT") return Promise.resolve(json({ ...document, ...JSON.parse(options.body as string) }));
      if (url.endsWith("/documents/8")) return Promise.resolve(json(document));
      if (url.endsWith("/users/1")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.endsWith("/document-types")) return Promise.resolve(json({ items: [{ id: 4, name: "Invoice" }] }));
      if (url.endsWith("/users/1/teams")) return Promise.resolve(json({ items: [{ team: { id: 7, name: "Design" }, role: "READ_WRITE" }] }));
      if (url.endsWith("/teams")) return Promise.resolve(json({ items: [{ id: 7, name: "Design", owner_id: 1 }] }));
      return Promise.resolve(json({ items: [] }));
    });
  };
  const openSample = async (user: UserEvent) => {
    render(<Home />);
    await user.click((await screen.findByRole("heading", { name: "Sample document" })).closest("button") as HTMLElement);
    return screen.getByRole("dialog", { name: /document details/i });
  };

  it("persists the theme choice and reflects the initial document theme", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Home />);
    const toggle = await screen.findByRole("button", { name: /toggle color mode/i });
    expect(toggle).toHaveAttribute("aria-pressed", "false");
    await user.click(toggle);
    expect(document.documentElement).toHaveClass("dark");
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    expect(localStorage.getItem("paperless_theme")).toBe("dark");
    unmount();
    render(<Home />);
    expect(await screen.findByRole("button", { name: /toggle color mode/i })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: /toggle color mode/i }));
    expect(localStorage.getItem("paperless_theme")).toBe("light");
  });

  it("uses system dark mode until a preference is chosen", async () => {
    const matchMedia = jest.spyOn(window, "matchMedia").mockImplementation((query) => ({ matches: query === "(prefers-color-scheme: dark)", media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn() }));
    try {
      render(<Home />);
      expect(await screen.findByRole("button", { name: /toggle color mode/i })).toHaveAttribute("aria-pressed", "true");
      expect(localStorage.getItem("paperless_theme")).toBeNull();
    } finally { matchMedia.mockRestore(); }
  });

  it("opens the upload panel from the primary action", async () => {
    render(<Home />);
    await userEvent.click(screen.getAllByRole("button", { name: /upload document/i })[0]);
    expect(screen.getByRole("dialog", { name: /upload document/i })).toBeInTheDocument();
  });

  it("keeps workspace navigation keyboard accessible", async () => {
    const user = userEvent.setup();
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /settings/i }));
    expect(await screen.findByRole("heading", { name: /workspace settings/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Open navigation" })).toHaveAttribute("aria-expanded", "false");
  });

  it("opens and closes the mobile drawer with the keyboard", async () => {
    const user = userEvent.setup();
    render(<Home />);
    const trigger = screen.getByRole("button", { name: "Open navigation" });
    expect(screen.queryByRole("navigation", { name: "Main navigation" })).not.toBeInTheDocument();
    await user.click(trigger);
    expect(screen.getByRole("navigation", { name: "Main navigation" })).toBeInTheDocument();
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    await user.keyboard("{Escape}");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(document.activeElement).toBe(trigger);
  });

  it("shows one persistent navigation on desktop", () => {
    const matchMedia = jest.spyOn(window, "matchMedia").mockImplementation((query) => ({ matches: query === "(min-width:1200px)", media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn() }));
    try {
      render(<Home />);
      expect(screen.getAllByRole("navigation", { name: "Main navigation" })).toHaveLength(1);
      expect(screen.queryByRole("button", { name: "Open navigation" })).not.toBeInTheDocument();
    } finally { matchMedia.mockRestore(); }
  });

  it("shows login failure without hiding the workspace", async () => {
    render(<Home />);
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));
    await userEvent.type(screen.getByLabelText(/username/i), "ada");
    await userEvent.type(screen.getByLabelText(/password/i), "wrong");
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 401, statusText: "Unauthorized", json: async () => ({ detail: "The supplied credentials are invalid." }) });
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /sign in/i }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("alert")).toHaveTextContent("The supplied credentials are invalid.");
    expect(within(document.getElementById("workspace-shell")!).queryByRole("alert")).not.toBeInTheDocument();
    expect(document.getElementById("workspace-shell")).toBeInTheDocument();
  });

  it("bootstraps documents and metadata after a successful login", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (url.endsWith("/auth/login")) return Promise.resolve({ ok: true, status: 200, json: async () => ({ token: "fresh", user: { id: 1, username: "ada", email: "ada@example.com" } }) });
      if (url.endsWith("/documents?page=0&size=12&sort=created_at%2Cdesc")) return Promise.resolve({ ok: true, status: 200, json: async () => ({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }) });
      return Promise.resolve({ ok: true, status: 200, json: async () => ({ items: [] }) });
    });
    render(<Home />);
    await user.click(screen.getByRole("button", { name: /^sign in$/i }));
    await user.type(screen.getByLabelText(/username/i), "ada");
    await user.type(screen.getByLabelText(/password/i), "secret");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^sign in$/i }));
    expect(await screen.findByText(/your desk is ready/i)).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith("/api/document-types", expect.anything());
    expect(fetch).toHaveBeenCalledWith("/api/teams", expect.anything());
    expect(fetch).toHaveBeenCalledWith("/api/users/1/teams", expect.anything());
  });

  it("freezes the submitted search query while paginating", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (url.includes("page=0")) return Promise.resolve(json({ items: [{ document: { id: 1, title: "First result", original_filename: "first.txt", content_type: "text/plain", file_size: 100, status: "COMPLETED", created_at: "today" } }], pagination: { page: 0, size: 12, total_elements: 13, total_pages: 2 } }));
      return Promise.resolve(json({ items: [{ document: { id: 2, title: "Second result", original_filename: "second.txt", content_type: "text/plain", file_size: 100, status: "COMPLETED", created_at: "today" } }], pagination: { page: 1, size: 12, total_elements: 13, total_pages: 2 } }));
    });
    render(<Home />);
    const input = screen.getByRole("textbox", { name: /search by title/i });
    await user.type(input, "first query");
    await user.click(within(input.closest("form") as HTMLElement).getByRole("button", { name: /^search$/i }));
    expect(await screen.findByText("First result")).toBeInTheDocument();
    await user.clear(input);
    await user.type(input, "changed input");
    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(await screen.findByText("Second result")).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith("/api/search?query=first+query&fuzzy=true&page=1&size=12", expect.anything());
    expect(fetch).not.toHaveBeenCalledWith("/api/search?query=changed+input&fuzzy=true&page=1&size=12", expect.anything());
  });

  it("reports search pagination failures", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string) => url.includes("page=0")
      ? Promise.resolve(json({ items: [{ document: { id: 1, title: "First result", original_filename: "first.txt", content_type: "text/plain", file_size: 100, status: "COMPLETED", created_at: "today" } }], pagination: { page: 0, size: 12, total_elements: 13, total_pages: 2 } }))
      : Promise.resolve(json({ detail: "Search page failed" }, 500)));
    render(<Home />);
    const input = screen.getByRole("textbox", { name: /search by title/i });
    await user.type(input, "query");
    await user.click(within(input.closest("form") as HTMLElement).getByRole("button", { name: /^search$/i }));
    await screen.findByText("First result");
    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Search page failed");
  });

  it("uses labelled MUI menus for library filters", async () => {
    const user = userEvent.setup();
    mockWorkspace();
    render(<Home />);
    await choose(user, await screen.findByRole("combobox", { name: "Document type" }), "Invoice");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));
    expect(fetch).toHaveBeenCalledWith("/api/documents?page=0&size=12&sort=created_at%2Cdesc&document_type_id=4", expect.anything());
  });

  it("updates document metadata through the styled detail menus", async () => {
    const user = userEvent.setup();
    mockWorkspace();
    const dialog = await openSample(user);
    await choose(user, within(dialog).getByRole("combobox", { name: "Document type" }), "Invoice");
    await choose(user, within(dialog).getByRole("combobox", { name: "Share with team" }), "Design");
    await user.click(within(dialog).getByRole("button", { name: /save changes/i }));
    expect(fetch).toHaveBeenCalledWith("/api/documents/8", expect.objectContaining({ method: "PUT", body: JSON.stringify({ title: "Sample document", document_type_id: 4, team_id: 7, clear_team: false }) }));
  });

  it.each([
    ["sample.png", "image/png", "image"],
    ["sample.pdf", "application/pdf", "pdf"],
    ["sample.txt", "text/plain", "text"],
    ["sample.md", "text/markdown", "text"],
  ])("previews %s as soon as the details open", async (filename, mime, kind) => {
    const user = userEvent.setup();
    mockWorkspace(sampleDocument(filename, mime));
    jest.mocked(URL.createObjectURL).mockClear();
    jest.mocked(URL.revokeObjectURL).mockClear();
    const dialog = await openSample(user);
    expect(fetch).toHaveBeenCalledWith("/api/documents/8/download", expect.anything());
    if (kind === "image") expect(await within(dialog).findByRole("img", { name: "Sample document" })).toHaveAttribute("src", "blob:test");
    if (kind === "pdf") expect(await within(dialog).findByTitle("Sample document")).toHaveAttribute("sandbox", "allow-scripts");
    if (kind === "text") {
      const preview = await within(dialog).findByText(/# Notes/);
      expect(preview).toHaveTextContent("<script>unsafe</script>");
      expect(dialog.querySelector("script")).toBeNull();
    } else {
      expect(URL.createObjectURL).toHaveBeenCalledWith(expect.objectContaining({ type: mime }));
      await user.click(within(dialog).getByRole("button", { name: "Close Document details" }));
      expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:test");
    }
  });

  it.each([
    ["unsafe.svg", "image/svg+xml"],
    ["unsafe.html", "text/html"],
    ["disguised.png", "image/svg+xml"],
  ])("offers download but not preview for %s", async (filename, mime) => {
    const user = userEvent.setup();
    mockWorkspace(sampleDocument(filename, mime));
    const dialog = await openSample(user);
    expect(within(dialog).queryByRole("button", { name: "Preview" })).not.toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Download" })).toBeInTheDocument();
    expect(within(dialog).getByText(/preview is unavailable/i)).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalledWith("/api/documents/8/download", expect.anything());
  });

  it("shows preview failures without affecting download", async () => {
    const user = userEvent.setup();
    mockWorkspace();
    const originalFetch = global.fetch;
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => url.endsWith("/download") ? Promise.resolve({ ok: false, status: 503 }) : originalFetch(url, options));
    const dialog = await openSample(user);
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Download failed");
    expect(within(dialog).queryByRole("button", { name: "Preview" })).not.toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Download" })).toBeInTheDocument();
  });

  it("ignores an in-flight preview after closing the detail dialog", async () => {
    const user = userEvent.setup();
    mockWorkspace();
    const originalFetch = global.fetch;
    const pending = Promise.withResolvers<unknown>();
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => url.endsWith("/download") ? pending.promise : originalFetch(url, options));
    jest.mocked(URL.createObjectURL).mockClear();
    const dialog = await openSample(user);
    expect(within(dialog).getByText("Loading preview…")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Close Document details" }));
    await act(async () => { pending.resolve({ ok: true, status: 200, blob: async () => new Blob(["late"]) }); });
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it("manages team members from the team detail view", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    const candidate = { id: 2, username: "ada" };
    const team = { id: 3, name: "Editors", description: "Write access", owner_id: 1 };
    const member = { user: candidate, role: "READ_WRITE", joined_at: "today" };
    const memberState = { items: [] as unknown[] };
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "owner", email: "owner@example.com" }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/document-types")) return Promise.resolve(json({ items: [] }));
      if (url.endsWith("/users/1/teams")) return Promise.resolve(json({ items: [] }));
      if (url.endsWith("/teams")) return Promise.resolve(json({ items: [team] }));
      if (url.endsWith("/users")) return Promise.resolve(json({ items: [candidate] }));
      if (url.endsWith("/teams/3/members") && options?.method === "POST") { memberState.items = [member]; return Promise.resolve(json(member)); }
      if (url.endsWith("/teams/3/members/2") && options?.method === "PUT") { memberState.items = [{ ...member, role: "READONLY" }]; return Promise.resolve(json({ ...member, role: "READONLY" })); }
      if (url.endsWith("/teams/3/members/2")) { memberState.items = []; return Promise.resolve(json(undefined, 204)); }
      if (url.endsWith("/teams/3/members")) return Promise.resolve(json(memberState));
      if (url.endsWith("/teams/3")) return Promise.resolve(json(team));
      return Promise.resolve(json(undefined, 204));
    });
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /teams/i }));
    await screen.findByRole("heading", { name: /your teams/i });
    await user.click(screen.getByText("Editors"));
    await screen.findByText("Members");
    await user.click(screen.getByRole("button", { name: /add member/i }));
    const dialog = await screen.findByRole("dialog", { name: /add member to editors/i });
    await user.click(within(dialog).getByText("ada"));
    await user.click(within(dialog).getByRole("button", { name: /^create$/i }));
    expect(fetch).toHaveBeenCalledWith("/api/teams/3/members", expect.objectContaining({ method: "POST", body: JSON.stringify({ user_id: 2, role: "READ_WRITE" }) }));
    expect(await screen.findByText("ada")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /change role/i }));
    const roleDialog = await screen.findByRole("dialog", { name: /change role for ada/i });
    await choose(user, within(roleDialog).getByRole("combobox", { name: "Role" }), "Read Only");
    await user.click(within(roleDialog).getByRole("button", { name: /save changes/i }));
    expect(fetch).toHaveBeenCalledWith("/api/teams/3/members/2", expect.objectContaining({ method: "PUT" }));
    expect(await screen.findByText("Read Only")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /remove member/i }));
    const removeDialog = await screen.findByRole("dialog", { name: /remove ada from editors/i });
    await user.click(within(removeDialog).getByRole("button", { name: /^delete$/i }));
    expect(fetch).toHaveBeenCalledWith("/api/teams/3/members/2", expect.objectContaining({ method: "DELETE" }));
    expect(await screen.findByText(/no members yet/i)).toBeInTheDocument();
  });

  it("edits a document type through the settings dialog", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    const docType = { id: 8, name: "Acme", description: "Old notes" };
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/document-types") && !options?.method) return Promise.resolve(json({ items: [docType] }));
      if (url.endsWith("/document-types/8") && options?.method === "PUT") {
        const body = JSON.parse(options.body as string);
        docType.name = body.name;
        docType.description = body.description;
        return Promise.resolve(json(docType));
      }
      if (url.endsWith("/teams") || url.endsWith("/users/1/teams")) return Promise.resolve(json({ items: [] }));
      return Promise.resolve(json(undefined, 204));
    });
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /settings/i }));
    await screen.findByRole("heading", { name: /workspace settings/i });
    await user.click(screen.getByRole("button", { name: /^edit$/i }));
    const dialog = await screen.findByRole("dialog", { name: /edit document type/i });
    const nameInput = within(dialog).getByLabelText(/name/i);
    const descInput = within(dialog).getByLabelText(/description/i);
    await user.clear(nameInput);
    await user.type(nameInput, "Acme updated");
    await user.clear(descInput);
    await user.type(descInput, "New notes");
    await user.click(within(dialog).getByRole("button", { name: /save changes/i }));
    expect(fetch).toHaveBeenCalledWith("/api/document-types/8", expect.objectContaining({ method: "PUT", body: JSON.stringify({ name: "Acme updated", description: "New notes" }) }));
    expect(await screen.findByText("Acme updated")).toBeInTheDocument();
  });

  it("keeps focus inside an open dialog and closes with Escape", async () => {
    const user = userEvent.setup();
    render(<Home />);
    const trigger = screen.getAllByRole("button", { name: /upload document/i })[0];
    await user.click(trigger);
    const dialog = screen.getByRole("dialog", { name: /upload document/i });
    await user.tab();
    expect(dialog).toContainElement(document.activeElement as HTMLElement);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: /upload document/i })).not.toBeInTheDocument();
    expect(document.activeElement).toBe(trigger);
  });

  it("uses a blank search state rather than stale library cards", async () => {
    const user = userEvent.setup();
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /search/i }));
    expect(await screen.findByRole("textbox", { name: /search by title/i })).toHaveValue("");
    expect(screen.getAllByRole("heading", { name: /search your archive/i })[0]).toBeInTheDocument();
  });

  it("clears completed search state when re-entering Search", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string) => url.startsWith("/api/search?")
      ? Promise.resolve(json({ items: [{ document: { id: 1, title: "Completed result", original_filename: "result.txt", content_type: "text/plain", file_size: 100, status: "COMPLETED", created_at: "today" } }], pagination: { page: 0, size: 12, total_elements: 1, total_pages: 1 } }))
      : Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } })));
    render(<Home />);
    const input = screen.getByRole("textbox", { name: /search by title/i });
    await user.type(input, "completed query");
    await user.click(within(input.closest("form") as HTMLElement).getByRole("button", { name: /^search$/i }));
    expect(await screen.findByText("Completed result")).toBeInTheDocument();
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /library/i }));
    await screen.findByRole("heading", { name: /good documents/i });
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /search/i }));
    expect(await screen.findByRole("textbox", { name: /search by title/i })).toHaveValue("");
    expect(screen.getAllByRole("heading", { name: /search your archive/i }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("heading", { name: /results for/i })).not.toBeInTheDocument();
  });

  it("closes existing panels before opening login after an authenticated 401", async () => {
    const user = userEvent.setup();
    render(<Home />);
    const uploadTrigger = screen.getAllByRole("button", { name: /upload document/i })[0];
    await user.click(uploadTrigger);
    await act(async () => { window.dispatchEvent(new Event("paperless:unauthorized")); });
    expect(await screen.findByRole("dialog", { name: /welcome back/i })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: /upload document/i })).not.toBeInTheDocument();
    expect(screen.getAllByRole("dialog")).toHaveLength(1);
    expect(document.getElementById("workspace-shell")?.parentElement).toHaveAttribute("aria-hidden", "true");
  });

  it("registers a new account and signs the user in", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/users") && options?.method === "POST") return Promise.resolve(json({ id: 2, username: "newbie", email: "newbie@example.com" }, 201));
      if (url.endsWith("/auth/login")) return Promise.resolve(json({ token: "reg_token", user: { id: 2, username: "newbie", email: "newbie@example.com" } }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      return Promise.resolve(json({ items: [] }));
    });
    render(<Home />);
    await user.click(screen.getByRole("button", { name: /^sign in$/i }));
    await user.click(screen.getByRole("tab", { name: /register/i }));
    expect(screen.getByRole("dialog", { name: /create an account/i })).toBeInTheDocument();
    await user.type(screen.getByLabelText(/username/i), "newbie");
    await user.type(screen.getByLabelText(/password/i), "secret123");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^register$/i }));
    expect(await within(screen.getByRole("dialog")).findByRole("alert")).toHaveTextContent("Registration successful. Please sign in.");
    await user.type(screen.getByLabelText(/password/i), "secret123");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^sign in$/i }));
    expect(await screen.findByText(/your desk is ready/i)).toBeInTheDocument();
    expect(localStorage.getItem("paperless_token")).toBe("reg_token");
  });

  it("shows registration failure only in the modal and not on the page", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      statusText: "Conflict",
      json: async () => ({ detail: "Username already exists." }),
    });
    render(<Home />);
    await user.click(screen.getByRole("button", { name: /^sign in$/i }));
    await user.click(screen.getByRole("tab", { name: /register/i }));
    await user.type(screen.getByLabelText(/username/i), "existing");
    await user.type(screen.getByLabelText(/password/i), "secret123");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /^register$/i }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Username already exists.");
    expect(within(document.getElementById("workspace-shell")!).queryByRole("alert")).not.toBeInTheDocument();
  });

  it("confirms document deletion with a modal instead of browser confirm", async () => {
    const user = userEvent.setup();
    mockWorkspace(sampleDocument());
    render(<Home />);
    expect(await screen.findByText("Sample document")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /delete sample document/i }));
    const confirmModal = await screen.findByRole("dialog", { name: /delete document/i });
    expect(within(confirmModal).getByText(/are you sure you want to delete this document/i)).toBeInTheDocument();
    await user.click(within(confirmModal).getByRole("button", { name: /cancel/i }));
    expect(screen.queryByRole("dialog", { name: /delete document/i })).not.toBeInTheDocument();
    expect(screen.getByText("Sample document")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /delete sample document/i }));
    const confirmModal2 = await screen.findByRole("dialog", { name: /delete document/i });
    await user.click(within(confirmModal2).getByRole("button", { name: /^delete$/i }));
    expect(fetch).toHaveBeenCalledWith("/api/documents/8", expect.objectContaining({ method: "DELETE" }));
    expect(await screen.findByText(/your desk is ready/i)).toBeInTheDocument();
  });

  it("updates the profile from workspace settings", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.endsWith("/users/1") && options?.method === "PUT") return Promise.resolve(json({ id: 1, username: "ada_updated", email: "ada@example.com" }));
      if (url.endsWith("/users/1")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/document-types") || url.endsWith("/teams") || url.endsWith("/users/1/teams")) return Promise.resolve(json({ items: [] }));
      return Promise.resolve(json(undefined, 204));
    });
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /settings/i }));
    await screen.findByRole("heading", { name: /workspace settings/i });
    await user.type(screen.getByLabelText(/username/i), "ada_updated");
    await user.click(screen.getByRole("button", { name: /save changes/i }));
    expect(fetch).toHaveBeenCalledWith("/api/users/1", expect.objectContaining({ method: "PUT", body: JSON.stringify({ username: "ada_updated" }) }));
    await openNavigation(user);
    expect(await screen.findByText("ada_updated")).toBeInTheDocument();
  });

  it("shows each team role on the team cards", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.endsWith("/users/1/teams")) return Promise.resolve(json({ items: [{ team: { id: 5, name: "Core Team" }, role: "ADMIN" }] }));
      if (url.endsWith("/teams")) return Promise.resolve(json({ items: [{ id: 5, name: "Core Team", description: "", owner_id: 99 }] }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/document-types")) return Promise.resolve(json({ items: [] }));
      return Promise.resolve(json({ items: [] }));
    });
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /teams/i }));
    await screen.findByRole("heading", { name: /your teams/i });
    expect(await screen.findByText("Core Team")).toBeInTheDocument();
    expect(screen.getByText("Administrator")).toBeInTheDocument();
  });

  it("edits a team with a modal instead of prompt", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    const team = { id: 3, name: "DevOps", description: "Infra", owner_id: 1 };
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.endsWith("/users/1/teams")) return Promise.resolve(json({ items: [] }));
      if (url.endsWith("/teams/3") && options?.method === "PUT") { const body = JSON.parse(options.body as string); team.name = body.name; team.description = body.description; return Promise.resolve(json(team)); }
      if (url.endsWith("/teams")) return Promise.resolve(json({ items: [team] }));
      if (url.endsWith("/teams/3")) return Promise.resolve(json(team));
      if (url.endsWith("/teams/3/members")) return Promise.resolve(json({ items: [] }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/document-types")) return Promise.resolve(json({ items: [] }));
      return Promise.resolve(json(undefined, 204));
    });
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /teams/i }));
    await screen.findByRole("heading", { name: /your teams/i });
    await user.click(screen.getByText("DevOps"));
    await screen.findByText("Members");
    await user.click(screen.getByRole("button", { name: /edit team/i }));
    const dialog = await screen.findByRole("dialog", { name: /edit team/i });
    const input = within(dialog).getByLabelText(/team name/i);
    await user.clear(input);
    await user.type(input, "Platform");
    await user.click(within(dialog).getByRole("button", { name: /save changes/i }));
    expect(fetch).toHaveBeenCalledWith("/api/teams/3", expect.objectContaining({ method: "PUT" }));
    expect(await screen.findByText("Platform")).toBeInTheDocument();
  });
});
