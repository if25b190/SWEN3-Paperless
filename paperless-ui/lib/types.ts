export type Role = "ADMIN" | "READ_WRITE" | "READONLY";

export const ROLES: Role[] = ["ADMIN", "READ_WRITE", "READONLY"];

export type ProcessingStatus =
  | "PENDING"
  | "OCR_IN_PROGRESS"
  | "GENAI_IN_PROGRESS"
  | "COMPLETED"
  | "FAILED";

export const PROCESSING_STATUSES: ProcessingStatus[] = [
  "PENDING",
  "OCR_IN_PROGRESS",
  "GENAI_IN_PROGRESS",
  "COMPLETED",
  "FAILED",
];

export interface InvalidParam {
  name: string;
  reason: string;
}

export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  invalid_params?: InvalidParam[];
}

export interface PageMetadata {
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface User {
  id: string;
  username: string;
  created_at: string;
  updated_at?: string;
}

export interface LoginResponse {
  token: string;
  token_type: string;
  user: User;
}

export interface Team {
  id: string;
  name: string;
  owner_id: string;
  description?: string | null;
  created_at: string;
  updated_at?: string;
}

export interface TeamMember {
  user: User;
  role: Role;
  joined_at?: string;
}

export interface UserTeamMembership {
  team: Team;
  role: Role;
}

export interface DocumentType {
  id: string;
  name: string;
  description?: string | null;
}

export interface DocumentItem {
  id: string;
  owner_id: string;
  team_id?: string | null;
  title: string;
  original_filename: string;
  content_type: string;
  file_size: number;
  status: ProcessingStatus;
  ocr_content?: string | null;
  summary?: string | null;
  storage_key?: string;
  document_type?: DocumentType | null;
  created_at: string;
  updated_at: string;
}

export interface DocumentPage {
  pagination: PageMetadata;
  items: DocumentItem[];
}

export interface SearchResultItem {
  document: DocumentItem;
  score: number;
  highlights?: string[];
}

export interface SearchPage {
  pagination: PageMetadata;
  items: SearchResultItem[];
}

export interface UpdateDocumentPayload {
  title?: string;
  document_type_id?: string | null;
  team_id?: string | null;
  clear_team?: boolean;
}
