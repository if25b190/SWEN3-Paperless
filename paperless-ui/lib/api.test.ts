import { apiFetch, apiUpload, ApiError } from "./api";

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

  it("sends multipart uploads without overriding the content type", async () => {
    global.fetch = jest.fn().mockResolvedValue(response({ id: 1 }, 201));
    const file = new File(["hello"], "hello.txt", { type: "text/plain" });
    await apiUpload("/documents", { document: file, title: "Hello" });
    expect(fetch).toHaveBeenCalledWith("/api/documents", expect.objectContaining({ method: "POST", body: expect.any(FormData) }));
    expect((fetch as jest.Mock).mock.calls[0][1].headers["Content-Type"]).toBeUndefined();
  });
});
