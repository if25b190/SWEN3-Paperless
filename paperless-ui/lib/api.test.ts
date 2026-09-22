import { api, apiFetch, apiUpload, apiDelete, apiGetFile, apiUpdateDocument, normalizeApiError, ApiError } from "./api";

describe("api client", () => {
  const response = (body: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, statusText: "Nope", json: async () => body });
  beforeEach(() => {
    localStorage.setItem("paperless_token", "abc");
    jest.resetAllMocks();
  });

  it("attaches the stored bearer token and parses JSON", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 4 }));
    await expect(apiFetch<{ id: number }>("/documents")).resolves.toEqual({ id: 4 });
    const request = (fetch as jest.Mock).mock.calls[0][1];
    expect(request.headers.get("Authorization")).toBe("Bearer abc");
  });

  it("turns non-success responses into useful errors", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ message: "Nope" }, 401));
    await expect(apiFetch("/auth/me")).rejects.toEqual(expect.any(ApiError));
  });

  it("uses RFC 7807 detail before title", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ detail: "Specific problem", title: "Bad request" }, 400));
    await expect(apiFetch("/documents")).rejects.toMatchObject({ message: "Specific problem" });
    expect(normalizeApiError({ title: "Fallback" })).toBe("Fallback");
  });

  it("removes the token only for unauthorized responses", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ detail: "Expired" }, 401));
    await expect(apiFetch("/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(localStorage.getItem("paperless_token")).toBeNull();
  });

  it("preserves the token when an unauthenticated request gets 401", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ detail: "Bad credentials" }, 401));
    await expect(apiFetch("/auth/login", { method: "POST", auth: false, body: "{}" })).rejects.toMatchObject({ status: 401 });
    expect(localStorage.getItem("paperless_token")).toBe("abc");
  });

  it("sends a required non-empty password for user creation", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 2 }));
    await api.createUser({ username: "ada", email: "ada@example.com", password: "secret123" });
    expect(JSON.parse((fetch as jest.Mock).mock.calls[0][1].body)).toMatchObject({ password: "secret123" });
  });

  it("exposes detail and membership endpoint callers", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 1, name: "Item" }));
    await api.document(1); await api.user(2); await api.userTeams(2); await api.team(3); await api.members(3); await api.users();
    expect((fetch as jest.Mock).mock.calls.map(([url]) => url)).toEqual([
      "/api/documents/1", "/api/users/2", "/api/users/2/teams", "/api/teams/3", "/api/teams/3/members", "/api/users",
    ]);
  });

  it("uses correspondent notes and exposes metadata detail callers", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 8, name: "Acme", notes: "Preferred" }));
    await api.correspondent(8);
    await api.documentType(9);
    await api.createLabel("correspondents", { name: "Acme", notes: "Preferred" });
    await api.updateLabel("correspondents", 8, { notes: "Updated" });
    expect((fetch as jest.Mock).mock.calls.map(([url]) => url)).toEqual([
      "/api/correspondents/8", "/api/document-types/9", "/api/correspondents", "/api/correspondents/8",
    ]);
    expect(JSON.parse((fetch as jest.Mock).mock.calls[2][1].body)).toEqual({ name: "Acme", notes: "Preferred" });
    expect(JSON.parse((fetch as jest.Mock).mock.calls[3][1].body)).toEqual({ notes: "Updated" });
  });

  it("exposes team member assignment mutations", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ user: { id: 2 }, role: "MEMBER" }));
    await api.addMember(3, { user_id: 2, role: "MEMBER" });
    await api.updateMember(3, 2, "ADMIN");
    await api.removeMember(3, 2);
    expect((fetch as jest.Mock).mock.calls.map(([url]) => url)).toEqual([
      "/api/teams/3/members", "/api/teams/3/members/2", "/api/teams/3/members/2",
    ]);
    expect(JSON.parse((fetch as jest.Mock).mock.calls[0][1].body)).toEqual({ user_id: 2, role: "MEMBER" });
  });

  it("sends multipart uploads without overriding the content type", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 1 }, 201));
    const file = new File(["hello"], "hello.txt", { type: "text/plain" });
    await apiUpload("/documents", { document: file, title: "Hello" });
    expect(fetch).toHaveBeenCalledWith("/api/documents", expect.objectContaining({ method: "POST", body: expect.any(FormData) }));
    expect((fetch as jest.Mock).mock.calls[0][1].headers.has("Content-Type")).toBe(false);
  });

  it("uses real Headers for mutations and file downloads", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 8 }));
    await apiUpdateDocument(8, { title: "Renamed" });
    await apiDelete("/document-types/8");
    expect((fetch as jest.Mock).mock.calls[0][0]).toBe("/api/documents/8");
    expect((fetch as jest.Mock).mock.calls[0][1].headers).toBeInstanceOf(Headers);
    global.fetch = jest.fn().mockResolvedValue({ ok: true, blob: async () => new Blob(["file"]) });
    await apiGetFile(8, "receipt.pdf");
    expect((fetch as jest.Mock).mock.calls[0][0]).toBe("/api/documents/8/download");
  });
});
