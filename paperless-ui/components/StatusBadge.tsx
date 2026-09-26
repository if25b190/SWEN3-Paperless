"use client";

import React from "react";
import { ProcessingStatus } from "@/lib/types/api";
import { useTranslation } from "@/lib/i18n/context";
import { Badge } from "./ui/badge";
import { Clock, Loader2, Sparkles, CheckCircle2, AlertTriangle } from "lucide-react";

interface StatusBadgeProps {
  status: ProcessingStatus;
  showIcon?: boolean;
}

export function StatusBadge({ status, showIcon = true }: StatusBadgeProps) {
  const { t } = useTranslation();

  switch (status) {
    case "PENDING":
      return (
        <Badge variant="warning" className="gap-1 font-medium">
          {showIcon && <Clock className="h-3 w-3" />}
          {t.status.PENDING}
        </Badge>
      );
    case "OCR_IN_PROGRESS":
      return (
        <Badge variant="info" className="gap-1 font-medium">
          {showIcon && <Loader2 className="h-3 w-3 animate-spin" />}
          {t.status.OCR_IN_PROGRESS}
        </Badge>
      );
    case "GENAI_IN_PROGRESS":
      return (
        <Badge variant="purple" className="gap-1 font-medium">
          {showIcon && <Sparkles className="h-3 w-3 animate-pulse" />}
          {t.status.GENAI_IN_PROGRESS}
        </Badge>
      );
    case "COMPLETED":
      return (
        <Badge variant="success" className="gap-1 font-medium">
          {showIcon && <CheckCircle2 className="h-3 w-3" />}
          {t.status.COMPLETED}
        </Badge>
      );
    case "FAILED":
      return (
        <Badge variant="destructive" className="gap-1 font-medium">
          {showIcon && <AlertTriangle className="h-3 w-3" />}
          {t.status.FAILED}
        </Badge>
      );
    default:
      return <Badge variant="outline">{status}</Badge>;
  }
}
