import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Home from "./page";

describe("Paperless workspace", () => {
  it("opens the upload panel from the primary action", async () => {
    render(<Home />);
    await userEvent.click(screen.getByRole("button", { name: /upload document/i }));
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
    expect(screen.getByRole("button", { name: /upload document/i })).toBeInTheDocument();
  });
});
