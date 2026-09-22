export type ApiOptions = RequestInit & { auth?: boolean };

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); this.name = "ApiError"; }
}
const token = () => typeof window === "undefined" ? null : localStorage.getItem("paperless_token");
export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const bearer = token(); if (bearer && options.auth !== false) headers.set("Authorization", `Bearer ${bearer}`);
  const response = await fetch(`/api${path}`, { ...options, headers });
  if (!response.ok) { let message = response.statusText || "Request failed"; try { message = (await response.json()).message || message; } catch { /* empty response */ } throw new ApiError(response.status, message); }
  if (response.status === 204) return undefined as T; return response.json() as Promise<T>;
}
export function apiUpload<T>(path: string, values: Record<string, string | number | File | null | undefined>): Promise<T> { const body = new FormData(); Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined) body.append(key, value instanceof File ? value : String(value)); }); return apiFetch<T>(path, { method: "POST", body }); }
export async function apiDownload(path: string, filename: string) { const response = await fetch(`/api${path}`, { headers: token() ? { Authorization: `Bearer ${token()}` } : {} }); if (!response.ok) throw new ApiError(response.status, "Download failed"); const url = URL.createObjectURL(await response.blob()); const link = document.createElement("a"); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url); }
export const query = (values: Record<string, string | number | boolean | null | undefined>) => { const params = new URLSearchParams(); Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== "") params.set(key, String(value)); }); return params.toString(); };
export type User = { id: number; username: string; email: string; created_at: string };
export type Team = { id: number; name: string; description?: string | null };
export type Label = { id: number; name: string; notes?: string | null };
export type Document = { id: number; title: string; original_filename: string; content_type: string; file_size: number; status: string; summary?: string | null; correspondent?: Label | null; document_type?: Label | null; created_at: string };
export type Page<T> = { items: T[]; pagination: { page: number; size: number; total_elements: number; total_pages: number } };
