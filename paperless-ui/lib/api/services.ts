import { apiBlob, apiFetch, type RequestOptions } from "@/lib/api/client";
import type {
  DocumentItem,
  DocumentPage,
  DocumentType,
  LoginResponse,
  Role,
  SearchPage,
  Team,
  TeamMember,
  UpdateDocumentPayload,
  User,
  UserTeamMembership,
} from "@/lib/types";

type Opts = Pick<RequestOptions, "silent" | "messages" | "signal" | "anonymous">;

export const authApi = {
  login: (body: { username: string; password: string }, opts?: Opts) =>
    apiFetch<LoginResponse>("/auth/login", { method: "POST", body, ...opts }),
  me: (opts?: Opts) => apiFetch<User>("/auth/me", opts),
};

export const usersApi = {
  register: (body: { username: string; password: string }, opts?: Opts) =>
    apiFetch<User>("/users", { method: "POST", body, ...opts }),
  list: (opts?: Opts) => apiFetch<{ items: User[] }>("/users", opts).then((r) => r.items),
  update: (id: string, body: { username?: string; password?: string }, opts?: Opts) =>
    apiFetch<User>(`/users/${id}`, { method: "PUT", body, ...opts }),
  remove: (id: string, opts?: Opts) => apiFetch<void>(`/users/${id}`, { method: "DELETE", ...opts }),
  teams: (id: string, opts?: Opts) =>
    apiFetch<{ items: UserTeamMembership[] }>(`/users/${id}/teams`, opts).then((r) => r.items),
};

export const teamsApi = {
  list: (opts?: Opts) => apiFetch<{ items: Team[] }>("/teams", opts).then((r) => r.items),
  get: (id: string, opts?: Opts) => apiFetch<Team>(`/teams/${id}`, opts),
  create: (body: { name: string; description?: string | null }, opts?: Opts) =>
    apiFetch<Team>("/teams", { method: "POST", body, ...opts }),
  update: (id: string, body: { name?: string; description?: string | null }, opts?: Opts) =>
    apiFetch<Team>(`/teams/${id}`, { method: "PUT", body, ...opts }),
  remove: (id: string, opts?: Opts) => apiFetch<void>(`/teams/${id}`, { method: "DELETE", ...opts }),
  members: (id: string, opts?: Opts) =>
    apiFetch<{ items: TeamMember[] }>(`/teams/${id}/members`, opts).then((r) => r.items),
  addMember: (id: string, body: { user_id: string; role: Role }, opts?: Opts) =>
    apiFetch<TeamMember>(`/teams/${id}/members`, { method: "POST", body, ...opts }),
  updateMemberRole: (id: string, userId: string, role: Role, opts?: Opts) =>
    apiFetch<TeamMember>(`/teams/${id}/members/${userId}`, {
      method: "PUT",
      body: { role },
      ...opts,
    }),
  removeMember: (id: string, userId: string, opts?: Opts) =>
    apiFetch<void>(`/teams/${id}/members/${userId}`, { method: "DELETE", ...opts }),
};

export interface DocumentListParams {
  page?: number;
  size?: number;
  sort?: string;
  document_type_id?: string;
}

export const documentsApi = {
  list: (params: DocumentListParams, opts?: Opts) =>
    apiFetch<DocumentPage>("/documents", { query: { ...params }, ...opts }),
  get: (id: string, opts?: Opts) => apiFetch<DocumentItem>(`/documents/${id}`, opts),
  upload: (
    input: { file: File; title: string; documentTypeId?: string; teamId?: string },
    opts?: Opts,
  ) => {
    const formData = new FormData();
    formData.append("document", input.file);
    formData.append("title", input.title);
    if (input.documentTypeId) formData.append("document_type_id", input.documentTypeId);
    if (input.teamId) formData.append("team_id", input.teamId);
    return apiFetch<DocumentItem>("/documents", { method: "POST", formData, ...opts });
  },
  update: (id: string, body: UpdateDocumentPayload, opts?: Opts) =>
    apiFetch<DocumentItem>(`/documents/${id}`, { method: "PUT", body, ...opts }),
  remove: (id: string, opts?: Opts) =>
    apiFetch<void>(`/documents/${id}`, { method: "DELETE", ...opts }),
  download: (id: string, opts?: Opts) => apiBlob(`/documents/${id}/download`, opts),
};

export const searchApi = {
  search: (
    params: { query: string; fuzzy: boolean; page?: number; size?: number },
    opts?: Opts,
  ) => apiFetch<SearchPage>("/search", { query: { ...params }, ...opts }),
};

export const documentTypesApi = {
  list: (opts?: Opts) =>
    apiFetch<{ items: DocumentType[] }>("/document-types", opts).then((r) => r.items),
  create: (body: { name: string; description?: string | null }, opts?: Opts) =>
    apiFetch<DocumentType>("/document-types", { method: "POST", body, ...opts }),
  update: (id: string, body: { name?: string; description?: string | null }, opts?: Opts) =>
    apiFetch<DocumentType>(`/document-types/${id}`, { method: "PUT", body, ...opts }),
  remove: (id: string, opts?: Opts) =>
    apiFetch<void>(`/document-types/${id}`, { method: "DELETE", ...opts }),
};
