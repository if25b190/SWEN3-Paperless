import { apiFetch, apiUpload, ApiError } from "./api";

describe("api client", () => {
  beforeEach(() => {
    localStorage.setItem("paperless_token", "abc");
    jest.resetAllMocks();
  });

  it("attaches the stored bearer token and parses JSON", async () => {
    global.fetch = jest.fn().mockResolvedValue(new Response(JSON.stringify({ id: 4 }), { status: 200 }));
    await expect(apiFetch<{ id: number }>("/documents")).resolves.toEqual({ id: 4 });
    expect(fetch).toHaveBeenCalledWith("/api/documents", expect.objectContaining({ headers: expect.objectContaining({ Authorization: "Bearer abc" }) }));
  });

  it("turns non-success responses into useful errors", async () => {
    global.fetch = jest.fn().mockResolvedValue(new Response(JSON.stringify({ message: "Nope" }), { status: 401 }));
    await expect(apiFetch("/auth/me")).rejects.toEqual(expect.any(ApiError));
  });

  it("sends multipart uploads without overriding the content type", async () => {
    global.fetch = jest.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }), { status: 201 }));
    const file = new File(["hello"], "hello.txt", { type: "text/plain" });
    await apiUpload("/documents", { document: file, title: "Hello" });
    expect(fetch).toHaveBeenCalledWith("/api/documents", expect.objectContaining({ method: "POST", body: expect.any(FormData) }));
    expect((fetch as jest.Mock).mock.calls[0][1].headers["Content-Type"]).toBeUndefined();
  });
});
