"use client";

import { useCallback, useSyncExternalStore } from "react";
import {
  getLocale,
  getServerLocale,
  setLocale,
  subscribeLocale,
  translate,
  type Locale,
  type TranslateParams,
} from "@/lib/i18n";

export function useI18n() {
  const locale = useSyncExternalStore(subscribeLocale, getLocale, getServerLocale);

  const t = useCallback(
    (key: string, params?: TranslateParams) => translate(locale, key, params),
    [locale],
  );

  const changeLocale = useCallback((next: Locale) => setLocale(next), []);

  return { locale, t, setLocale: changeLocale };
}
