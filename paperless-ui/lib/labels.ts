import type { ProcessingStatus, Role } from "@/lib/types";

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Administrator",
  READ_WRITE: "Read & Write",
  READONLY: "Read Only",
};

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  ADMIN: "Full access and member management.",
  READ_WRITE: "Can read, upload, edit and delete documents.",
  READONLY: "Can read, search and download documents.",
};

export const STATUS_LABELS: Record<ProcessingStatus, string> = {
  PENDING: "Pending",
  OCR_IN_PROGRESS: "OCR running",
  GENAI_IN_PROGRESS: "AI summary running",
  COMPLETED: "Completed",
  FAILED: "Failed",
};
