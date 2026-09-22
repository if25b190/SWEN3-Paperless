import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Home from "./page";

describe("Paperless workspace", () => {
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
  });

  it("keeps search ready and reports search pagination failures", async () => {
    const user = userEvent.setup();
    render(<Home />);
    await user.click(within(screen.getByRole("navigation", { name: /main navigation/i })).getByRole("button", { name: /search/i }));
    expect(screen.getAllByRole("heading", { name: /search your archive/i })[0]).toBeInTheDocument();
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
});
