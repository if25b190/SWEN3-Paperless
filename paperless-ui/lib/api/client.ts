import { toast } from "sonner";
import {
  Problem,
  UserResponse,
  UserListResponse,
  CreateUserRequest,
  UpdateUserRequest,
  LoginRequest,
  LoginResponse,
  TeamResponse,
  TeamListResponse,
  CreateTeamRequest,
  UpdateTeamRequest,
  TeamMemberResponse,
  TeamMemberListResponse,
  AddTeamMemberRequest,
  UpdateTeamMemberRoleRequest,
  UserTeamMembershipListResponse,
  DocumentResponse,
  DocumentPageResponse,
  UpdateDocumentRequest,
  SearchResponse,
  DocumentTypeResponse,
  DocumentTypeListResponse,
  CreateDocumentTypeRequest,
  UpdateDocumentTypeRequest,
} from "../types/api";

const TOKEN_KEY = "paperless_auth_token";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // ignore
  }
}

export function clearToken(): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
}

export class ApiError extends Error {
  status: number;
  problem?: Problem;

  constructor(status: number, message: string, problem?: Problem) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  get invalidParams() {
    return this.problem?.invalid_params || [];
  }
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {},
  customNotifyError = true
): Promise<T> {
  const token = getToken();
  const headers = new Headers(options.headers || {});

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  if (!(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`/api${endpoint}`, {
      ...options,
      headers,
    });
  } catch {
    if (customNotifyError) {
      toast.error("Could not connect to the server. Please check your network.");
    }
    throw new ApiError(0, "Network Error");
  }

  if (!response.ok) {
    let problem: Problem | undefined;
    try {
      const contentType = response.headers.get("content-type");
      if (contentType && (contentType.includes("json") || contentType.includes("problem"))) {
        problem = await response.json();
      }
    } catch {
      // ignore
    }

    const message =
      problem?.detail ||
      problem?.title ||
      response.statusText ||
      `HTTP error ${response.status}`;

    if (customNotifyError) {
      handleStatusError(response.status, message, problem);
    }

    throw new ApiError(response.status, message, problem);
  }

  if (response.status === 204) {
    return null as unknown as T;
  }

  return response.json();
}

function handleStatusError(status: number, message: string, problem?: Problem) {
  if (status === 401) {
    clearToken();
    toast.error(problem?.detail || "Your session has expired. Please sign in again.");
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.href = "/login";
    }
    return;
  }

  if (status === 403) {
    toast.error(problem?.detail || "You do not have permission to perform this action.");
    return;
  }

  if (status === 404) {
    toast.error(problem?.detail || "The requested resource was not found.");
    return;
  }

  if (status === 409) {
    toast.error(problem?.detail || "A conflict occurred. The resource might already exist.");
    return;
  }

  if (status >= 500) {
    toast.error("An unexpected server error occurred. Please try again later.");
    return;
  }

  // 400 or other errors
  toast.error(message);
}

export const api = {
  get: <T>(url: string, notifyError = true) =>
    request<T>(url, { method: "GET" }, notifyError),

  post: <T>(url: string, body?: unknown, notifyError = true) =>
    request<T>(
      url,
      {
        method: "POST",
        body: body ? JSON.stringify(body) : undefined,
      },
      notifyError
    ),

  put: <T>(url: string, body?: unknown, notifyError = true) =>
    request<T>(
      url,
      {
        method: "PUT",
        body: body ? JSON.stringify(body) : undefined,
      },
      notifyError
    ),

  delete: <T>(url: string, notifyError = true) =>
    request<T>(url, { method: "DELETE" }, notifyError),

  upload: <T>(url: string, formData: FormData, notifyError = true) =>
    request<T>(
      url,
      {
        method: "POST",
        body: formData,
      },
      notifyError
    ),
};

// ----------------- Auth API -----------------
export const authApi = {
  login: (data: LoginRequest) => api.post<LoginResponse>("/auth/login", data),
  me: (notifyError = false) => api.get<UserResponse>("/auth/me", notifyError),
};

// ----------------- Users API -----------------
export const usersApi = {
  register: (data: CreateUserRequest) => api.post<UserResponse>("/users", data),
  getUsers: () => api.get<UserListResponse>("/users"),
  getUserById: (id: string) => api.get<UserResponse>(`/users/${id}`),
  updateUser: (id: string, data: UpdateUserRequest) =>
    api.put<UserResponse>(`/users/${id}`, data),
  deleteUser: (id: string) => api.delete<void>(`/users/${id}`),
  getUserTeams: (id: string) =>
    api.get<UserTeamMembershipListResponse>(`/users/${id}/teams`),
};

// ----------------- Teams API -----------------
export const teamsApi = {
  getTeams: () => api.get<TeamListResponse>("/teams"),
  createTeam: (data: CreateTeamRequest) =>
    api.post<TeamResponse>("/teams", data),
  getTeamById: (id: string) => api.get<TeamResponse>(`/teams/${id}`),
  updateTeam: (id: string, data: UpdateTeamRequest) =>
    api.put<TeamResponse>(`/teams/${id}`, data),
  deleteTeam: (id: string) => api.delete<void>(`/teams/${id}`),
  getMembers: (id: string) =>
    api.get<TeamMemberListResponse>(`/teams/${id}/members`),
  addMember: (teamId: string, data: AddTeamMemberRequest) =>
    api.post<TeamMemberResponse>(`/teams/${teamId}/members`, data),
  updateMemberRole: (
    teamId: string,
    userId: string,
    data: UpdateTeamMemberRoleRequest
  ) =>
    api.put<TeamMemberResponse>(
      `/teams/${teamId}/members/${userId}`,
      data
    ),
  removeMember: (teamId: string, userId: string) =>
    api.delete<void>(`/teams/${teamId}/members/${userId}`),
};

// ----------------- Documents API -----------------
export const documentsApi = {
  getDocuments: (params?: {
    page?: number;
    size?: number;
    sort?: string;
    document_type_id?: string;
  }) => {
    const q = new URLSearchParams();
    if (params?.page !== undefined) q.set("page", params.page.toString());
    if (params?.size !== undefined) q.set("size", params.size.toString());
    if (params?.sort) q.set("sort", params.sort);
    if (params?.document_type_id)
      q.set("document_type_id", params.document_type_id);
    const queryStr = q.toString() ? `?${q.toString()}` : "";
    return api.get<DocumentPageResponse>(`/documents${queryStr}`);
  },
  uploadDocument: (formData: FormData) =>
    api.upload<DocumentResponse>("/documents", formData),
  getDocumentById: (id: string) =>
    api.get<DocumentResponse>(`/documents/${id}`),
  updateDocument: (id: string, data: UpdateDocumentRequest) =>
    api.put<DocumentResponse>(`/documents/${id}`, data),
  deleteDocument: (id: string) => api.delete<void>(`/documents/${id}`),
  getDownloadUrl: (id: string, preview = false) => {
    const token = getToken();
    const params = new URLSearchParams();
    if (token) params.set("token", token);
    if (preview) params.set("preview", "true");
    const q = params.toString() ? `?${params.toString()}` : "";
    return `/api/documents/${id}/download${q}`;
  },
  downloadDocument: async (id: string, filename?: string) => {
    const token = getToken();
    const headers = new Headers();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
    try {
      const response = await fetch(`/api/documents/${id}/download`, {
        method: "GET",
        headers,
      });
      if (!response.ok) {
        toast.error("Failed to download document file.");
        return;
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename || "document.pdf";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error("Network error while downloading document.");
    }
  },
  fetchDocumentBlobUrl: async (id: string): Promise<string> => {
    const token = getToken();
    const headers = new Headers();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
    const response = await fetch(`/api/documents/${id}/download`, {
      method: "GET",
      headers,
    });
    if (!response.ok) {
      throw new Error(`Failed to load document preview (HTTP ${response.status})`);
    }
    const blob = await response.blob();
    return window.URL.createObjectURL(blob);
  },
};

// ----------------- Search API -----------------
export const searchApi = {
  search: (params: {
    query: string;
    fuzzy?: boolean;
    page?: number;
    size?: number;
  }) => {
    const q = new URLSearchParams();
    q.set("query", params.query);
    if (params.fuzzy !== undefined) q.set("fuzzy", params.fuzzy.toString());
    if (params.page !== undefined) q.set("page", params.page.toString());
    if (params.size !== undefined) q.set("size", params.size.toString());
    return api.get<SearchResponse>(`/search?${q.toString()}`);
  },
};

// ----------------- Document Types API -----------------
export const documentTypesApi = {
  getDocumentTypes: () =>
    api.get<DocumentTypeListResponse>("/document-types"),
  getDocumentTypeById: (id: string) =>
    api.get<DocumentTypeResponse>(`/document-types/${id}`),
  createDocumentType: (data: CreateDocumentTypeRequest) =>
    api.post<DocumentTypeResponse>("/document-types", data),
  updateDocumentType: (id: string, data: UpdateDocumentTypeRequest) =>
    api.put<DocumentTypeResponse>(`/document-types/${id}`, data),
  deleteDocumentType: (id: string) =>
    api.delete<void>(`/document-types/${id}`),
};
