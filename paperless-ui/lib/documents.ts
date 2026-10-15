import { documentsApi } from "@/lib/api/services";
import type { DocumentItem } from "@/lib/types";

export async function downloadDocument(document: DocumentItem) {
  const blob = await documentsApi.download(document.id);
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement("a");
  link.href = url;
  link.download = document.original_filename || `${document.title}.pdf`;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

export const NONE_VALUE = "__none__";
export const PRIVATE_VALUE = "__private__";
