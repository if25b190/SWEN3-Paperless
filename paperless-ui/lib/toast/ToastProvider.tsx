"use client";

import { createContext, useCallback, useContext, useState } from "react";
import { Alert, Snackbar } from "@mui/material";

type ToastTone = "success" | "error" | "info";
type Toast = { id: number; tone: ToastTone; text: string };

const ToastContext = createContext<{ toast: (tone: ToastTone, text: string) => void } | null>(null);

let nextToastId = 0;

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const current = toasts[0] ?? null;

  const toast = useCallback((tone: ToastTone, text: string) => {
    setToasts((items) => [...items, { id: nextToastId++, tone, text }]);
  }, []);

  const dismiss = useCallback(() => {
    setToasts((items) => items.slice(1));
  }, []);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      {current !== null && (
        <Snackbar
          open
          autoHideDuration={4500}
          onClose={dismiss}
          anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        >
          <Alert severity={current.tone} role="alert">
            {current.text}
          </Alert>
        </Snackbar>
      )}
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast must be used within ToastProvider");
  return context;
}
