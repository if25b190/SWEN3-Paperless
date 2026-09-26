export type Role = "ADMIN" | "READONLY" | "READ_WRITE";

export interface UserResponse {
  id: string;
  username: string;
  created_at: string;
  updated_at?: string;
}

export interface UserListResponse {
  items: UserResponse[];
}

export interface CreateUserRequest {
  username: string;
  password: string;
}

export interface UpdateUserRequest {
  username?: string;
  password?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  token_type: string;
  user: UserResponse;
}

export interface TeamResponse {
  id: string;
  name: string;
  owner_id: string;
  description?: string | null;
  created_at: string;
  updated_at?: string;
}

export interface TeamListResponse {
  items: TeamResponse[];
}

export interface CreateTeamRequest {
  name: string;
  description?: string | null;
}

export interface UpdateTeamRequest {
  name?: string;
  description?: string | null;
}

export interface TeamMemberResponse {
  user: UserResponse;
  role: Role;
  joined_at?: string;
}

export interface TeamMemberListResponse {
  items: TeamMemberResponse[];
}

export interface AddTeamMemberRequest {
  user_id: string;
  role: Role;
}

export interface UpdateTeamMemberRoleRequest {
  role: Role;
}

export interface UserTeamMembershipResponse {
  team: TeamResponse;
  role: Role;
}

export interface UserTeamMembershipListResponse {
  items: UserTeamMembershipResponse[];
}

export type ProcessingStatus =
  | "PENDING"
  | "OCR_IN_PROGRESS"
  | "GENAI_IN_PROGRESS"
  | "COMPLETED"
  | "FAILED";

export interface DocumentTypeResponse {
  id: string;
  name: string;
  description?: string | null;
}

export interface DocumentTypeListResponse {
  items: DocumentTypeResponse[];
}

export interface CreateDocumentTypeRequest {
  name: string;
  description?: string | null;
}

export interface UpdateDocumentTypeRequest {
  name?: string;
  description?: string | null;
}

export interface DocumentResponse {
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
  document_type?: DocumentTypeResponse | null;
  created_at: string;
  updated_at?: string;
}

export interface PageMetadata {
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface DocumentPageResponse {
  pagination: PageMetadata;
  items: DocumentResponse[];
}

export interface UpdateDocumentRequest {
  team_id?: string | null;
  clear_team?: boolean;
  title?: string;
  document_type_id?: string | null;
}

export interface SearchResultItem {
  document: DocumentResponse;
  score: number;
  highlights?: string[];
}

export interface SearchResponse {
  pagination: PageMetadata;
  items: SearchResultItem[];
}

export interface InvalidParam {
  name: string;
  reason: string;
}

export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  invalid_params?: InvalidParam[];
}
