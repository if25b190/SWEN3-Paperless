import { act, render as rtlRender, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import Home from "./page";
import { theme } from "./providers";

Object.defineProperty(window, "matchMedia", { writable: true, value: (query: string) => ({ matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn() }) });
const render = (ui: React.ReactNode) => rtlRender(<ThemeProvider theme={theme} defaultMode="system" modeStorageKey="paperless_theme">{ui}</ThemeProvider>);

describe("Paperless workspace", () => {
  const json = (body: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, statusText: "Request failed", json: async () => body });
  beforeEach(() => { localStorage.clear(); document.documentElement.classList.remove("dark"); });
  const openNavigation = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole("button", { name: "Open navigation" }));
    return screen.getByRole("navigation", { name: "Main navigation" });
  };
  const choose = async (user: ReturnType<typeof userEvent.setup>, field: HTMLElement, option: string) => {
    await user.click(field);
    await user.click(await screen.findByRole("option", { name: option }));
  };
  const sampleDocument = (filename = "sample.png", contentType = "image/png") => ({ id: 8, title: "Sample document", original_filename: filename, content_type: contentType, file_size: 128, status: "DONE", created_at: "today" });
  const mockWorkspace = (document = sampleDocument(), content = "# Notes\n<script>unsafe</script>") => {
    localStorage.setItem("paperless_token", "token");
    const blob = new Blob([content], { type: "application/octet-stream" });
    Object.defineProperty(blob, "text", { value: async () => content });
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [document], pagination: { page: 0, size: 12, total_elements: 1, total_pages: 1 } }));
      if (url.endsWith("/documents/8/download")) return Promise.resolve({ ok: true, status: 200, blob: async () => blob });
      if (url.endsWith("/documents/8") && options?.method === "PUT") return Promise.resolve(json({ ...document, ...JSON.parse(options.body as string) }));
      if (url.endsWith("/documents/8")) return Promise.resolve(json(document));
      if (url.endsWith("/correspondents/3")) return Promise.resolve(json({ id: 3, name: "Acme" }));
      if (url.endsWith("/document-types/4")) return Promise.resolve(json({ id: 4, name: "Invoice" }));
      if (url.endsWith("/correspondents")) return Promise.resolve(json({ items: [{ id: 3, name: "Acme" }] }));
      if (url.endsWith("/document-types")) return Promise.resolve(json({ items: [{ id: 4, name: "Invoice" }] }));
      return Promise.resolve(json({ items: [] }));
    });
  };
  const openSample = async (user: ReturnType<typeof userEvent.setup>) => {
    render(<Home />);
    await user.click((await screen.findByRole("heading", { name: "Sample document" })).closest("button") as HTMLElement);
    return screen.getByRole("dialog", { name: /document details/i });
  };

  it("persists the theme choice and reflects the initial document theme", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Home />);
    const toggle = await screen.findByRole("button", { name: "Dark mode" });
    expect(toggle).toHaveAttribute("aria-pressed", "false");
    await user.click(toggle);
    expect(document.documentElement).toHaveClass("dark");
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    expect(localStorage.getItem("paperless_theme")).toBe("dark");
    unmount();
    render(<Home />);
    expect(await screen.findByRole("button", { name: "Dark mode" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Dark mode" }));
    expect(localStorage.getItem("paperless_theme")).toBe("light");
  });

  it("uses system dark mode until a preference is chosen", async () => {
    const matchMedia = jest.spyOn(window, "matchMedia").mockImplementation((query) => ({ matches: query === "(prefers-color-scheme: dark)", media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn() }));
    try {
      render(<Home />);
      expect(await screen.findByRole("button", { name: "Dark mode" })).toHaveAttribute("aria-pressed", "true");
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
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 401, statusText: "Unauthorized", json: async () => ({ detail: "Wrong password" }) });
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: /sign in/i }));
    expect((await screen.findAllByRole("alert"))[0]).toHaveTextContent("Wrong password");
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
    expect(fetch).toHaveBeenCalledWith("/api/correspondents", expect.anything());
    expect(fetch).toHaveBeenCalledWith("/api/document-types", expect.anything());
    expect(fetch).toHaveBeenCalledWith("/api/teams", expect.anything());
    expect(fetch).toHaveBeenCalledWith("/api/users", expect.anything());
  });

  it("freezes the submitted search query while paginating", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (url.includes("page=0")) return Promise.resolve(json({ items: [{ document: { id: 1, title: "First result", original_filename: "first.txt", content_type: "text/plain", file_size: 100, status: "DONE", created_at: "today" } }], pagination: { page: 0, size: 12, total_elements: 13, total_pages: 2 } }));
      return Promise.resolve(json({ items: [{ document: { id: 2, title: "Second result", original_filename: "second.txt", content_type: "text/plain", file_size: 100, status: "DONE", created_at: "today" } }], pagination: { page: 1, size: 12, total_elements: 13, total_pages: 2 } }));
    });
    render(<Home />);
    const input = screen.getByRole("textbox", { name: /search documents/i });
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
      ? Promise.resolve(json({ items: [{ document: { id: 1, title: "First result", original_filename: "first.txt", content_type: "text/plain", file_size: 100, status: "DONE", created_at: "today" } }], pagination: { page: 0, size: 12, total_elements: 13, total_pages: 2 } }))
      : Promise.resolve(json({ detail: "Search page failed" }, 500)));
    render(<Home />);
    const input = screen.getByRole("textbox", { name: /search documents/i });
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
    await choose(user, await screen.findByRole("combobox", { name: "Correspondent" }), "Acme");
    await choose(user, screen.getByRole("combobox", { name: "Document type" }), "Invoice");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("correspondent_id=3&document_type_id=4"), expect.anything());
  });

  it("updates document metadata through the styled detail menus", async () => {
    const user = userEvent.setup();
    mockWorkspace();
    const dialog = await openSample(user);
    await choose(user, within(dialog).getByRole("combobox", { name: "Document correspondent" }), "Acme");
    await choose(user, within(dialog).getByRole("combobox", { name: "Document type" }), "Invoice");
    await user.click(within(dialog).getByRole("button", { name: "Save changes" }));
    expect(fetch).toHaveBeenCalledWith("/api/documents/8", expect.objectContaining({ method: "PUT", body: JSON.stringify({ title: "Sample document", correspondent_id: 3, document_type_id: 4 }) }));
  });

  it.each([
    ["sample.png", "image/png", "image"],
    ["sample.pdf", "application/pdf", "pdf"],
    ["sample.txt", "text/plain", "text"],
    ["sample.md", "text/markdown", "text"],
  ])("previews %s only when requested", async (filename, mime, kind) => {
    const user = userEvent.setup();
    mockWorkspace(sampleDocument(filename, mime));
    jest.mocked(URL.createObjectURL).mockClear();
    jest.mocked(URL.revokeObjectURL).mockClear();
    const dialog = await openSample(user);
    expect(fetch).not.toHaveBeenCalledWith("/api/documents/8/download", expect.anything());
    await user.click(within(dialog).getByRole("button", { name: "Preview" }));
    expect(fetch).toHaveBeenCalledWith("/api/documents/8/download", expect.anything());
    if (kind === "image") expect(await within(dialog).findByRole("img", { name: "Preview of Sample document" })).toHaveAttribute("src", "blob:test");
    if (kind === "pdf") expect(await within(dialog).findByTitle("Preview of Sample document")).toHaveAttribute("sandbox", "allow-scripts");
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
    await user.click(within(dialog).getByRole("button", { name: "Preview" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Download failed");
    expect(within(dialog).getByRole("button", { name: "Preview" })).toBeEnabled();
    expect(within(dialog).getByRole("button", { name: "Download" })).toBeInTheDocument();
  });

  it("ignores an in-flight preview after closing the detail dialog", async () => {
    const user = userEvent.setup();
    mockWorkspace();
    const originalFetch = global.fetch;
    let resolveDownload!: (value: unknown) => void;
    const pending = new Promise((resolve) => { resolveDownload = resolve; });
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => url.endsWith("/download") ? pending : originalFetch(url, options));
    jest.mocked(URL.createObjectURL).mockClear();
    const dialog = await openSample(user);
    await user.click(within(dialog).getByRole("button", { name: "Preview" }));
    expect(within(dialog).getByText("Loading preview…")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Close Document details" }));
    await act(async () => { resolveDownload({ ok: true, status: 200, blob: async () => new Blob(["late"]) }); });
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it("loads users before assigning and keeps members scoped to their team", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    const ada = { id: 2, username: "ada", email: "ada@example.com", created_at: "today" };
    const member = { user: ada, role: "MEMBER" as const };
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json(ada));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/correspondents") || url.endsWith("/document-types")) return Promise.resolve(json({ items: [] }));
      if (url.endsWith("/teams")) return Promise.resolve(json({ items: [{ id: 3, name: "Editors" }, { id: 4, name: "Reviewers" }] }));
      if (url.endsWith("/users")) return Promise.resolve(json({ items: [ada] }));
      if (url.endsWith("/teams/3/members") && options?.method === "POST") return Promise.resolve(json(member));
      if (url.endsWith("/teams/3/members")) return Promise.resolve(json({ items: [] }));
      if (url.endsWith("/teams/3/members/2") && options?.method === "PUT") return Promise.resolve(json({ ...member, role: "READONLY" }));
      if (url.endsWith("/teams/4/members")) return Promise.resolve(json({ items: [] }));
      return Promise.resolve(json(undefined, 204));
    });
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /people/i }));
    await screen.findByRole("heading", { name: /your people/i });
    expect(fetch).toHaveBeenCalledWith("/api/users", expect.anything());
    const team = screen.getByText("Editors").closest("[data-team-id]") as HTMLElement;
    await user.click(within(team).getByRole("button", { name: "Members" }));
    const otherTeam = screen.getByText("Reviewers").closest("[data-team-id]") as HTMLElement;
    await user.click(within(otherTeam).getByRole("button", { name: "Members" }));
    await choose(user, within(team).getByRole("combobox", { name: /new member role/i }), "ADMIN");
    expect(within(otherTeam).getByRole("combobox", { name: /new member role/i })).toHaveTextContent("MEMBER");
    await choose(user, within(team).getByRole("combobox", { name: /member user/i }), "ada");
    await user.click(within(team).getByRole("button", { name: "Add" }));
    expect(fetch).toHaveBeenCalledWith("/api/teams/3/members", expect.objectContaining({ method: "POST" }));
    expect(within(team).getAllByText("ada")[0]).toBeInTheDocument();
    await choose(user, within(team).getByRole("combobox", { name: /role for ada/i }), "READONLY");
    expect(fetch).toHaveBeenCalledWith("/api/teams/3/members/2", expect.objectContaining({ method: "PUT" }));
    await user.click(within(team).getByRole("button", { name: "Remove" }));
    expect(within(team).queryByRole("combobox", { name: /role for ada/i })).not.toBeInTheDocument();
  });

  it("loads metadata details before editing correspondent notes", async () => {
    const user = userEvent.setup();
    localStorage.setItem("paperless_token", "token");
    global.fetch = jest.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/auth/me")) return Promise.resolve(json({ id: 1, username: "ada", email: "ada@example.com" }));
      if (url.includes("/documents?")) return Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } }));
      if (url.endsWith("/correspondents")) return Promise.resolve(json({ items: [{ id: 8, name: "Acme", notes: "Old notes" }] }));
      if (url.endsWith("/document-types") || url.endsWith("/teams") || url.endsWith("/users")) return Promise.resolve(json({ items: [] }));
      if (url.endsWith("/correspondents/8") && options?.method === "PUT") return Promise.resolve(json({ id: 8, name: "Acme updated", notes: "New notes" }));
      if (url.endsWith("/correspondents/8")) return Promise.resolve(json({ id: 8, name: "Acme", notes: "Server notes" }));
      return Promise.resolve(json(undefined, 204));
    });
    jest.spyOn(window, "prompt").mockReturnValueOnce("Acme updated").mockReturnValueOnce("New notes");
    render(<Home />);
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /settings/i }));
    await screen.findByRole("heading", { name: /workspace settings/i });
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    const calls = (fetch as jest.Mock).mock.calls.filter(([url]) => url === "/api/correspondents/8");
    expect(calls[0][1].method).toBeUndefined();
    expect(calls[1][1].method).toBe("PUT");
    expect(JSON.parse(calls[1][1].body)).toEqual({ name: "Acme updated", notes: "New notes" });
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
    expect(await screen.findByRole("textbox", { name: /search documents/i })).toHaveValue("");
    expect(screen.getAllByRole("heading", { name: /search your archive/i })[0]).toBeInTheDocument();
  });

  it("clears completed search state when re-entering Search", async () => {
    const user = userEvent.setup();
    global.fetch = jest.fn().mockImplementation((url: string) => url.startsWith("/api/search?")
      ? Promise.resolve(json({ items: [{ document: { id: 1, title: "Completed result", original_filename: "result.txt", content_type: "text/plain", file_size: 100, status: "DONE", created_at: "today" } }], pagination: { page: 0, size: 12, total_elements: 1, total_pages: 1 } }))
      : Promise.resolve(json({ items: [], pagination: { page: 0, size: 12, total_elements: 0, total_pages: 0 } })));
    render(<Home />);
    const input = screen.getByRole("textbox", { name: /search documents/i });
    await user.type(input, "completed query");
    await user.click(within(input.closest("form") as HTMLElement).getByRole("button", { name: /^search$/i }));
    expect(await screen.findByText("Completed result")).toBeInTheDocument();
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /library/i }));
    await screen.findByRole("heading", { name: /good documents/i });
    await user.click(within(await openNavigation(user)).getByRole("button", { name: /search/i }));
    expect(await screen.findByRole("textbox", { name: /search documents/i })).toHaveValue("");
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
});
