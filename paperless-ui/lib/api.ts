/* eslint-disable @typescript-eslint/no-unused-vars */
export type ApiOptions = RequestInit & { auth?: boolean };

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); this.name = "ApiError"; }
}

export const normalizeApiError = (body: { detail?: string; title?: string; message?: string }) => body.detail || body.title || body.message || "Request failed";
const getToken = () => typeof window === "undefined" ? null : localStorage.getItem("paperless_token");

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const bearer = getToken();
  if (!headers.has("Content-Type") && options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (bearer && options.auth !== false) headers.set("Authorization", `Bearer ${bearer}`);
  const { auth: _auth, ...request } = options;
  const response = await fetch(`/api${path}`, { ...request, headers });
  if (!response.ok) {
    let body: { detail?: string; title?: string; message?: string } = {};
    try { body = await response.json(); } catch { /* no body */ }
    if (response.status === 401 && options.auth !== false && typeof window !== "undefined") {
      localStorage.removeItem("paperless_token");
      window.dispatchEvent(new Event("paperless:unauthorized"));
    }
    throw new ApiError(response.status, normalizeApiError(body) || response.statusText);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function apiUpload<T>(path: string, values: Record<string, string | number | File | null | undefined>) {
  const body = new FormData();
  Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined) body.append(key, value instanceof File ? value : String(value)); });
  return apiFetch<T>(path, { method: "POST", body });
}

export async function apiGetFile(id: number, filename: string) {
  const response = await fetch(`/api/documents/${id}/download`, { headers: new Headers(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}) });
  if (!response.ok) { if (response.status === 401) { localStorage.removeItem("paperless_token"); window.dispatchEvent(new Event("paperless:unauthorized")); } throw new ApiError(response.status, "Download failed"); }
  const url = URL.createObjectURL(await response.blob()); const link = document.createElement("a"); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url);
}

export const apiDelete = (path: string) => apiFetch<void>(path, { method: "DELETE" });
export const apiUpdateDocument = (id: number, body: { title?: string; correspondent_id?: number | null; document_type_id?: number | null }) => apiFetch<Document>(`/documents/${id}`, { method: "PUT", body: JSON.stringify(body) });
export const query = (values: Record<string, string | number | boolean | null | undefined>) => { const params = new URLSearchParams(); Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== "") params.set(key, String(value)); }); return params.toString(); };
export type User = { id: number; username: string; email: string; created_at: string };
export type Team = { id: number; name: string; description?: string | null };
export type Correspondent = { id: number; name: string; description?: string | null };
export type DocumentType = { id: number; name: string; description?: string | null };
export type Label = Correspondent | DocumentType;
export type Document = { id: number; title: string; original_filename: string; content_type: string; file_size: number; status: string; summary?: string | null; correspondent?: Label | null; document_type?: Label | null; created_at: string };
export type Member = { user: User; role: "ADMIN" | "READONLY" | "MEMBER"; joined_at?: string };
export type Page<T> = { items: T[]; pagination: { page: number; size: number; total_elements: number; total_pages: number } };
export const api = {
  login: (body: { username: string; password: string }) => apiFetch<{ token: string; user: User }>("/auth/login", { method: "POST", body: JSON.stringify(body), auth: false }), me: () => apiFetch<User>("/auth/me"),
  documents: (params: Record<string, string | number | null | undefined>) => apiFetch<Page<Document>>(`/documents?${query(params)}`), document: (id: number) => apiFetch<Document>(`/documents/${id}`), search: (params: Record<string, string | number | boolean>) => apiFetch<Page<{ document: Document; score: number; highlights?: string[] }>>(`/search?${query(params)}`),
  labels: (kind: "correspondents" | "document-types") => apiFetch<{ items: Label[] }>(`/${kind}`), createLabel: (kind: "correspondents", body: { name: string; description?: string }) => apiFetch<Correspondent>(`/${kind}`, { method: "POST", body: JSON.stringify(body) }), createDocumentType: (body: { name: string; description?: string }) => apiFetch<DocumentType>("/document-types", { method: "POST", body: JSON.stringify(body) }), updateLabel: (kind: "correspondents", id: number, body: { name?: string; description?: string }) => apiFetch<Correspondent>(`/${kind}/${id}`, { method: "PUT", body: JSON.stringify(body) }), updateDocumentType: (id: number, body: { name?: string; description?: string }) => apiFetch<DocumentType>(`/document-types/${id}`, { method: "PUT", body: JSON.stringify(body) }), deleteLabel: (kind: "correspondents" | "document-types", id: number) => apiDelete(`/${kind}/${id}`),
  users: () => apiFetch<{ items: User[] }>("/users"), user: (id: number) => apiFetch<User>(`/users/${id}`), createUser: (body: { username: string; email: string; password: string }) => apiFetch<User>("/users", { method: "POST", body: JSON.stringify(body) }), updateUser: (id: number, body: Partial<{ username: string; email: string; password: string }>) => apiFetch<User>(`/users/${id}`, { method: "PUT", body: JSON.stringify(body) }), deleteUser: (id: number) => apiDelete(`/users/${id}`), userTeams: (id: number) => apiFetch<{ items: { team: Team; role: Member["role"] }[] }>(`/users/${id}/teams`),
  teams: () => apiFetch<{ items: Team[] }>("/teams"), team: (id: number) => apiFetch<Team>(`/teams/${id}`), createTeam: (body: { name: string; description?: string }) => apiFetch<Team>("/teams", { method: "POST", body: JSON.stringify(body) }), updateTeam: (id: number, body: { name?: string; description?: string }) => apiFetch<Team>(`/teams/${id}`, { method: "PUT", body: JSON.stringify(body) }), deleteTeam: (id: number) => apiDelete(`/teams/${id}`), members: (id: number) => apiFetch<{ items: Member[] }>(`/teams/${id}/members`), addMember: (id: number, body: { user_id: number; role: Member["role"] }) => apiFetch<Member>(`/teams/${id}/members`, { method: "POST", body: JSON.stringify(body) }), updateMember: (id: number, userId: number, role: Member["role"]) => apiFetch<Member>(`/teams/${id}/members/${userId}`, { method: "PUT", body: JSON.stringify({ role }) }), removeMember: (id: number, userId: number) => apiDelete(`/teams/${id}/members/${userId}`),
};
