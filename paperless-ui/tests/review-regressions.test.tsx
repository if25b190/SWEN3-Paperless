import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import DashboardPage from "@/app/(app)/page";
import TeamsPage from "@/app/(app)/teams/page";
import DocumentTypesPage from "@/app/(app)/document-types/page";
import { EditDocumentDialog } from "@/components/documents/edit-dialog";
import { TeamDialog } from "@/components/teams/team-dialog";
import { TooltipProvider } from "@/components/ui/tooltip";
import { documentsApi, documentTypesApi, teamsApi, usersApi } from "@/lib/api/services";
import type { DocumentItem, DocumentPage, Team, User } from "@/lib/types";

vi.mock("@/components/providers/auth-provider", () => ({
  useAuth: () => ({ user: { id: "user-1", username: "alice" } }),
}));

vi.mock("@/lib/api/services", () => ({
  documentsApi: { list: vi.fn(), update: vi.fn(), remove: vi.fn() },
  documentTypesApi: { list: vi.fn() },
  teamsApi: { update: vi.fn() },
  usersApi: { teams: vi.fn() },
}));

const user: User = { id: "user-1", username: "alice", created_at: "2026-10-20T00:00:00Z" };
const team: Team = { id: "team-1", name: "Finance", owner_id: user.id, description: "Old description", created_at: user.created_at };
const document: DocumentItem = {
  id: "document-1",
  owner_id: user.id,
  title: "First invoice",
  original_filename: "invoice.pdf",
  content_type: "application/pdf",
  file_size: 100,
  status: "COMPLETED",
  document_type: { id: "type-1", name: "Invoice" },
  created_at: user.created_at,
  updated_at: user.created_at,
};

function page(items: DocumentItem[], index = 0, total = 11): DocumentPage {
  return { items, pagination: { page: index, size: 10, total_elements: total, total_pages: Math.ceil(total / 10) } };
}

async function selectOption(trigger: HTMLElement, name: string) {
  fireEvent.keyDown(trigger, { key: "ArrowDown" });
  const option = await screen.findByRole("option", { name });
  fireEvent.keyDown(option, { key: "Enter" });
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(usersApi.teams).mockResolvedValue([{ team, role: "ADMIN" }]);
  vi.mocked(documentTypesApi.list).mockResolvedValue([document.document_type!]);
});

test("an empty sharing-filter result keeps navigation to matching documents on another page", async () => {
  vi.mocked(documentsApi.list).mockImplementation(async ({ page: index }) =>
    index === 1 ? page([document], 1) : page([{ ...document, id: "shared", team_id: team.id }]),
  );
  render(<TooltipProvider><DashboardPage /></TooltipProvider>);
  await screen.findByRole("link", { name: document.title });

  await selectOption(screen.getByRole("combobox", { name: "Sharing" }), "Private only");
  expect(await screen.findByText("No matching documents on this page")).toBeDefined();
  fireEvent.click(screen.getByRole("button", { name: "Next" }));

  expect(await screen.findByRole("link", { name: document.title })).toBeDefined();
  expect(screen.getByText("Page 2 of 2")).toBeDefined();
});

test("deleting the last document on the final page returns to a valid page", async () => {
  let deleted = false;
  const lastDocument = { ...document, id: "last", title: "Last invoice" };
  vi.mocked(documentsApi.list).mockImplementation(async ({ page: index }) =>
    index === 1 ? page(deleted ? [] : [lastDocument], 1, deleted ? 10 : 11) : page([document], 0, deleted ? 10 : 11),
  );
  vi.mocked(documentsApi.remove).mockImplementation(async () => { deleted = true; });
  render(<TooltipProvider><DashboardPage /></TooltipProvider>);
  await screen.findByRole("link", { name: document.title });
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await screen.findByRole("link", { name: lastDocument.title });
  fireEvent.click(screen.getByRole("button", { name: "Delete Document" }));
  fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete" }));

  expect(await screen.findByRole("link", { name: document.title })).toBeDefined();
  expect(documentsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
  expect(screen.queryByText("No documents found")).toBeNull();
});

test("selecting No type submits an explicit removal without a replacement type ID", async () => {
  vi.mocked(documentsApi.update).mockResolvedValue({ ...document, document_type: null });
  const onSaved = vi.fn();
  render(<EditDocumentDialog document={document} onOpenChange={vi.fn()} onSaved={onSaved} />);
  await selectOption(screen.getAllByRole("combobox")[0], "No type");
  fireEvent.click(screen.getByRole("button", { name: "Save" }));

  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ document_type: null })));
  expect(documentsApi.update).toHaveBeenCalledWith(document.id, {
    title: document.title, document_type_id: undefined, clear_document_type: true,
  });
});

test("clearing a team description sends an empty string to the partial-update API", async () => {
  vi.mocked(teamsApi.update).mockResolvedValue({ ...team, description: "" });
  const onSaved = vi.fn();
  render(<TeamDialog open team={team} onOpenChange={vi.fn()} onSaved={onSaved} />);
  fireEvent.change(screen.getByRole("textbox", { name: /Description/ }), { target: { value: "   " } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));

  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ description: "" })));
  expect(teamsApi.update).toHaveBeenCalledWith(team.id, { name: team.name, description: "" });
});

test("a failed teams request shows an error and Retry recovers the list", async () => {
  vi.mocked(usersApi.teams).mockRejectedValueOnce(new Error("offline"));
  render(<TeamsPage />);
  expect(await screen.findByText("Teams could not be loaded.")).toBeDefined();
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));

  expect(await screen.findByRole("heading", { name: team.name })).toBeDefined();
  expect(screen.queryByText("Teams could not be loaded.")).toBeNull();
});

test("a failed document-types request shows an error and Retry recovers the list", async () => {
  vi.mocked(documentTypesApi.list).mockRejectedValueOnce(new Error("offline"));
  render(<DocumentTypesPage />);
  expect(await screen.findByText("Document types could not be loaded.")).toBeDefined();
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));

  expect(await screen.findByRole("cell", { name: "Invoice" })).toBeDefined();
  expect(screen.queryByText("Document types could not be loaded.")).toBeNull();
});
