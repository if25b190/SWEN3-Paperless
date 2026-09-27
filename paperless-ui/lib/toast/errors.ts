import { ApiError } from "../api";

export type Translate = (key: string, vars?: Record<string, string | number>) => string;

export function describeError(error: unknown, t: Translate): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401: return t("errors.session_expired");
      case 403: return t("errors.forbidden");
      case 404: return t("errors.not_found");
      case 409: return error.problem.detail || t("errors.conflict");
      default: return error.message;
    }
  }
  if (error instanceof Error) return error.message;
  return t("common.error");
}
