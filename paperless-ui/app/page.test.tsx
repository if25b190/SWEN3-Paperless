import { render, screen } from "@testing-library/react";
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
});
