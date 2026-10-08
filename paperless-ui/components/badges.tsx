"use client";

import { Badge } from "@/components/ui/card";
import { useI18n } from "@/lib/i18n/use-i18n";
import type { ProcessingStatus, Role } from "@/lib/types";
import { Crown, Loader2, Pencil, Eye, CheckCircle2, Clock, XCircle } from "lucide-react";

const STATUS_VARIANT = {
  PENDING: "muted",
  OCR_IN_PROGRESS: "warning",
  GENAI_IN_PROGRESS: "warning",
  COMPLETED: "success",
  FAILED: "destructive",
} as const;

export function isProcessing(status: ProcessingStatus) {
  return status === "PENDING" || status === "OCR_IN_PROGRESS" || status === "GENAI_IN_PROGRESS";
}

export function StatusBadge({ status }: { status: ProcessingStatus }) {
  const { t } = useI18n();
  const Icon =
    status === "COMPLETED"
      ? CheckCircle2
      : status === "FAILED"
        ? XCircle
        : status === "PENDING"
          ? Clock
          : Loader2;
  return (
    <Badge variant={STATUS_VARIANT[status]}>
      <Icon className={`size-3 ${Icon === Loader2 ? "animate-spin" : ""}`} />
      {t(`status.${status}`)}
    </Badge>
  );
}

const ROLE_VARIANT = {
  ADMIN: "default",
  READ_WRITE: "secondary",
  READONLY: "outline",
} as const;

export function RoleBadge({ role }: { role: Role }) {
  const { t } = useI18n();
  const Icon = role === "ADMIN" ? Crown : role === "READ_WRITE" ? Pencil : Eye;
  return (
    <Badge variant={ROLE_VARIANT[role]}>
      <Icon className="size-3" />
      {t(`roles.${role}`)}
    </Badge>
  );
}
