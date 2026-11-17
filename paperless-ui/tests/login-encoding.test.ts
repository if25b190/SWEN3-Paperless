import { beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "@/lib/api/client";
import { authApi } from "@/lib/api/services";

vi.mock("@/lib/api/client", () => ({ apiFetch: vi.fn(), apiBlob: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

it.each([
  ["SecretP@ss123", "U2VjcmV0UEBzczEyMw=="],
  ["páß🔑", "cMOhw5/wn5SR"],
  ["  secret  ", "ICBzZWNyZXQgIA=="],
])("encodes the login password as UTF-8 Base64 (%s)", async (password, encoded) => {
  const credentials = { username: "alice", password };
  const opts = { anonymous: true, messages: { 401: "Invalid username or password." } };

  await authApi.login(credentials, opts);

  expect(apiFetch).toHaveBeenCalledWith("/auth/login", {
    method: "POST",
    body: { username: "alice", password: encoded },
    ...opts,
  });
  expect(credentials.password).toBe(password);
});
