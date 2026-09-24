import type { Document } from "../../lib/api";

export const bytes = (n: number) =>
  n > 1_000_000
    ? `${(n / 1_000_000).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1000))} KB`;
const previewMimeByExtension: Record<string, string> = {
  pdf: "application/pdf",
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  webp: "image/webp",
  avif: "image/avif",
  txt: "text/plain",
  md: "text/plain",
  markdown: "text/plain",
};

export function previewMime(document: Document): string | null {
  const extension =
    document.original_filename.toLowerCase().match(/\.([^.]+)$/)?.[1] || "";
  const safeMime = previewMimeByExtension[extension];
  if (!safeMime) return null;
  const declared = document.content_type.split(";")[0].trim().toLowerCase();
  if (
    declared === safeMime ||
    declared === "application/octet-stream" ||
    declared === ""
  )
    return safeMime;
  if (
    (extension === "md" || extension === "markdown") &&
    (declared === "text/markdown" || declared === "text/x-markdown")
  )
    return safeMime;
  return null;
}

export type PreviewState = {
  key: string;
  stage: "loading" | "ready" | "error";
  url?: string;
  text?: string;
  message?: string;
};
