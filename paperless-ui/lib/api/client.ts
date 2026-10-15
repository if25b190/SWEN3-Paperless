import { toast } from "sonner";
import { clearToken, getToken } from "@/lib/auth-token";
import type { Problem } from "@/lib/types";

const API_BASE = "/api/v1";

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: Problem;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem?: Problem, message?: string) {
    super(message ?? problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.fieldErrors = {};
    for (const param of problem?.invalid_params ?? []) {
      this.fieldErrors[param.name] = param.reason;
    }
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  formData?: FormData;
  /** Do not show a toast when the request fails. */
  silent?: boolean;
  /** Messages that override the default toast for a given HTTP status. */
  messages?: Partial<Record<number, string>>;
  signal?: AbortSignal;
  /** Do not attach the bearer token (login, registration). */
  anonymous?: boolean;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const url = `${API_BASE}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

async function parseProblem(response: Response): Promise<Problem | undefined> {
  const type = response.headers.get("content-type") ?? "";
  if (!type.includes("json")) return undefined;
  try {
    return (await response.json()) as Problem;
  } catch {
    return undefined;
  }
}

function notifyError(error: ApiError, options: RequestOptions, hadToken: boolean) {
  const { status, problem } = error;
  const override = options.messages?.[status];

  if (status === 401 && hadToken && !override) {
    clearToken();
    toast.error("Your session expired. Please sign in again.", { id: "session-expired" });
    return;
  }

  let message: string;
  if (override) {
    message = override;
  } else if (status === 401) {
    message = "Invalid username or password.";
  } else if (status === 403) {
    message = "You do not have permission to perform this action.";
  } else if (status === 404) {
    message = "The requested resource was not found.";
  } else if (status >= 500) {
    message = "An unexpected server error occurred. Please try again later.";
  } else if (status === 409) {
    message = problem?.detail || "This action conflicts with existing data.";
  } else {
    message = problem?.detail || problem?.title || "The request was not valid.";
  }

  const reasons = problem?.invalid_params?.map((param) => param.reason) ?? [];
  if (status === 400 && reasons.length > 0) {
    toast.error(message, {
      description: reasons.join(" "),
    });
    return;
  }
  toast.error(message);
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const token = options.anonymous ? null : getToken();
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let body: BodyInit | undefined;
  if (options.formData) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(options.body);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
      cache: "no-store",
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    const error = new ApiError(0, undefined, "Could not reach the server. Check your connection.");
    if (!options.silent) toast.error("Could not reach the server. Check your connection.", { id: "network-error" });
    throw error;
  }

  if (!response.ok) {
    const error = new ApiError(response.status, await parseProblem(response));
    if (!options.silent) notifyError(error, options, Boolean(token));
    throw error;
  }
  return response;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function apiBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
  const response = await send(path, options);
  return response.blob();
}
