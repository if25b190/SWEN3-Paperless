 import type { DocumentStatus } from "../../lib/api";

export const statusTone: Record<DocumentStatus, "default" | "info" | "warning" | "success" | "error"> = {
  PENDING: "default",
  OCR_IN_PROGRESS: "info",
  GENAI_IN_PROGRESS: "warning",
  COMPLETED: "success",
  FAILED: "error",
};

export const bytes = (n: number) =>
  n > 1_000_000
    ? `${(n / 1_000_000).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1000))} KB`;

export type HighlightSegment = { text: string; emphasized: boolean };

export function parseHighlights(text: string): HighlightSegment[] {
  const segments: HighlightSegment[] = [];
  const push = (chunk: string, emphasized: boolean) => {
    if (!chunk) return;
    const last = segments[segments.length - 1];
    if (last && last.emphasized === emphasized) last.text += chunk;
    else segments.push({ text: chunk, emphasized });
  };
  const pattern = /<em>([\s\S]*?)<\/em>/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text))) {
    push(text.slice(last, match.index), false);
    push(match[1], true);
    last = pattern.lastIndex;
  }
  push(text.slice(last), false);
  return segments;
}
