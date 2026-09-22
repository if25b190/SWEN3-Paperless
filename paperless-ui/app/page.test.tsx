import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Home from "./page";

describe("Paperless workspace", () => {
  const json = (body: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, statusText: "Request failed", json: async () => body });
  beforeEach(() => localStorage.clear());

  it("opens the upload panel from the primary action", async () => {
    render(<Home />);
    await userEvent.click(screen.getAllByRole("button", { name: /upload document/i })[0]);
    expect(screen.getByRole("dialog", { name: /upload document/i })).toBeInTheDocument();
  });

  it("keeps workspace navigation keyboard accessible", async () => {
    render(<Home />);
    await userEvent.click(screen.getByRole("button", { name: /settings/i }));
    expect(screen.getByRole("heading", { name: /workspace settings/i })).toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: /^search$/i }));
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
    await user.type(screen.getByRole("textbox", { name: /search documents/i }), "query");
    await user.click(screen.getByRole("button", { name: /^search$/i }));
    await screen.findByText("First result");
    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Search page failed");
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
    await user.click(await screen.findByRole("button", { name: /people/i }));
    expect(fetch).toHaveBeenCalledWith("/api/users", expect.anything());
    const team = screen.getByText("Editors").closest("div.rounded-xl") as HTMLElement;
    await user.click(within(team).getByRole("button", { name: "Members" }));
    const otherTeam = screen.getByText("Reviewers").closest("div.rounded-xl") as HTMLElement;
    await user.click(within(otherTeam).getByRole("button", { name: "Members" }));
    await user.selectOptions(within(team).getByRole("combobox", { name: /new member role/i }), "ADMIN");
    expect(within(otherTeam).getByRole("combobox", { name: /new member role/i })).toHaveValue("MEMBER");
    await user.selectOptions(within(team).getByRole("combobox", { name: /member user/i }), "2");
    await user.click(within(team).getByRole("button", { name: "Add" }));
    expect(fetch).toHaveBeenCalledWith("/api/teams/3/members", expect.objectContaining({ method: "POST" }));
    expect(within(team).getAllByText("ada")[0]).toBeInTheDocument();
    await user.selectOptions(within(team).getByRole("combobox", { name: /role for ada/i }), "READONLY");
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
    await user.click(await screen.findByRole("button", { name: /settings/i }));
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
    await user.click(within(screen.getByRole("navigation", { name: /main navigation/i })).getByRole("button", { name: /search/i }));
    expect(screen.getByRole("textbox", { name: /search documents/i })).toHaveValue("");
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
    await user.click(screen.getByRole("button", { name: /^search$/i }));
    expect(await screen.findByText("Completed result")).toBeInTheDocument();
    await user.click(within(screen.getByRole("navigation", { name: /main navigation/i })).getByRole("button", { name: /library/i }));
    await screen.findByRole("heading", { name: /good documents/i });
    await user.click(within(screen.getByRole("navigation", { name: /main navigation/i })).getByRole("button", { name: /search/i }));
    expect(screen.getByRole("textbox", { name: /search documents/i })).toHaveValue("");
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
    expect(document.getElementById("workspace-shell")).toHaveAttribute("aria-hidden", "true");
    expect((document.getElementById("workspace-shell") as HTMLElement & { inert?: boolean }).inert).toBe(true);
  });
});
