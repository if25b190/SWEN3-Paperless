export type ApiOptions = RequestInit & { auth?: boolean };

export type InvalidParam = { name: string; reason: string };

export type Problem = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  invalid_params?: InvalidParam[];
  message?: string;
};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public problem: Problem = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const normalizeApiError = (body: Problem): string =>
  body.detail || body.title || body.message || "Request failed";

export const invalidParamFor = (error: unknown, name: string): string | null => {
  if (error instanceof ApiError && error.problem.invalid_params) {
    const match = error.problem.invalid_params.find((param) => param.name === name);
    return match ? match.reason : null;
  }
  return null;
};

const getToken = () =>
  typeof window === "undefined" ? null : localStorage.getItem("paperless_token");

const clearSession = () => {
  if (typeof window === "undefined") return;
  localStorage.removeItem("paperless_token");
  window.dispatchEvent(new Event("paperless:unauthorized"));
};

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const bearer = getToken();
  const { auth, ...request } = options;
  if (!headers.has("Content-Type") && options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (bearer && auth !== false) headers.set("Authorization", `Bearer ${bearer}`);
  const response = await fetch(`/api${path}`, { ...request, headers });
  if (!response.ok) {
    let body: Problem = {};
    try { body = await response.json(); } catch { /* no body */ }
    if (response.status === 401 && auth !== false) clearSession();
    throw new ApiError(response.status, normalizeApiError(body), body);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function apiUpload<T>(path: string, values: Record<string, string | number | File | null | undefined>) {
  const body = new FormData();
  Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== "") body.append(key, value instanceof File ? value : String(value)); });
  return apiFetch<T>(path, { method: "POST", body });
}

export async function apiGetFileBlob(id: string): Promise<Blob> {
  const token = getToken();
  const response = await fetch(`/api/documents/${id}/download`, { headers: new Headers(token ? { Authorization: `Bearer ${token}` } : {}) });
  if (!response.ok) {
    if (response.status === 401) clearSession();
    throw new ApiError(response.status, "Download failed");
  }
  return response.blob();
}

export async function apiGetFile(id: string, filename: string) {
  const url = URL.createObjectURL(await apiGetFileBlob(id)); const link = document.createElement("a"); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url);
}

export const apiDelete = (path: string) => apiFetch<void>(path, { method: "DELETE" });

export type UpdateDocumentBody = {
  title?: string;
  document_type_id?: string | null;
  team_id?: string | null;
  clear_team?: boolean;
};

export const apiUpdateDocument = (id: string, body: UpdateDocumentBody) => apiFetch<Document>(`/documents/${id}`, { method: "PUT", body: JSON.stringify(body) });

export const query = (values: Record<string, string | number | boolean | null | undefined>) => { const params = new URLSearchParams(); Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== "") params.set(key, String(value)); }); return params.toString(); };

export type User = { id: string; username: string; created_at: string; updated_at?: string };
export type Role = "ADMIN" | "READONLY" | "READ_WRITE";
export type Team = { id: string; name: string; owner_id: string; description?: string | null; created_at: string; updated_at?: string };
export type DocumentType = { id: string; name: string; description?: string | null };
export type DocumentStatus = "PENDING" | "OCR_IN_PROGRESS" | "GENAI_IN_PROGRESS" | "COMPLETED" | "FAILED";
export type Document = {
  id: string;
  owner_id: string;
  team_id?: string | null;
  title: string;
  original_filename: string;
  content_type: string;
  file_size: number;
  status: DocumentStatus;
  ocr_content?: string | null;
  summary?: string | null;
  storage_key?: string;
  document_type?: DocumentType | null;
  created_at: string;
  updated_at?: string;
};
export type Member = { user: User; role: Role; joined_at?: string };
export type TeamMembership = { team: Team; role: Role };
export type SearchResultItem = { document: Document; score: number; highlights?: string[] };
export type Page<T> = { items: T[]; pagination: { page: number; size: number; total_elements: number; total_pages: number } };
export type LoginResponse = { token: string; token_type: string; user: User };

export const processingStatuses: DocumentStatus[] = ["PENDING", "OCR_IN_PROGRESS", "GENAI_IN_PROGRESS"];
export const isProcessing = (status: string) => processingStatuses.includes(status as DocumentStatus);

export const api = {
  login: (body: { username: string; password: string }) => apiFetch<LoginResponse>("/auth/login", { method: "POST", body: JSON.stringify(body), auth: false }),
  register: (body: { username: string; password: string }) => apiFetch<User>("/users", { method: "POST", body: JSON.stringify(body), auth: false }),
  me: () => apiFetch<User>("/auth/me"),
  documents: (params: { page?: number; size?: number; sort?: string; document_type_id?: string | null } = {}) => apiFetch<Page<Document>>(`/documents?${query(params)}`),
  document: (id: string) => apiFetch<Document>(`/documents/${id}`),
  search: (params: { query: string; fuzzy?: boolean; page?: number; size?: number }) => apiFetch<Page<SearchResultItem>>(`/search?${query(params)}`),
  documentTypes: () => apiFetch<{ items: DocumentType[] }>("/document-types"),
  documentType: (id: string) => apiFetch<DocumentType>(`/document-types/${id}`),
  createDocumentType: (body: { name: string; description?: string | null }) => apiFetch<DocumentType>("/document-types", { method: "POST", body: JSON.stringify(body) }),
  updateDocumentType: (id: string, body: { name?: string; description?: string | null }) => apiFetch<DocumentType>(`/document-types/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteDocumentType: (id: string) => apiDelete(`/document-types/${id}`),
  users: () => apiFetch<{ items: User[] }>("/users"),
  user: (id: string) => apiFetch<User>(`/users/${id}`),
  updateUser: (id: string, body: Partial<{ username: string; password: string }>) => apiFetch<User>(`/users/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  userTeams: (id: string) => apiFetch<{ items: TeamMembership[] }>(`/users/${id}/teams`),
  teams: () => apiFetch<{ items: Team[] }>("/teams"),
  team: (id: string) => apiFetch<Team>(`/teams/${id}`),
  createTeam: (body: { name: string; description?: string | null }) => apiFetch<Team>("/teams", { method: "POST", body: JSON.stringify(body) }),
  updateTeam: (id: string, body: { name?: string; description?: string | null }) => apiFetch<Team>(`/teams/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteTeam: (id: string) => apiDelete(`/teams/${id}`),
  members: (id: string) => apiFetch<{ items: Member[] }>(`/teams/${id}/members`),
  addMember: (id: string, body: { user_id: string; role: Role }) => apiFetch<Member>(`/teams/${id}/members`, { method: "POST", body: JSON.stringify(body) }),
  updateMember: (id: string, userId: string, role: Role) => apiFetch<Member>(`/teams/${id}/members/${userId}`, { method: "PUT", body: JSON.stringify({ role }) }),
  removeMember: (id: string, userId: string) => apiDelete(`/teams/${id}/members/${userId}`),
};
