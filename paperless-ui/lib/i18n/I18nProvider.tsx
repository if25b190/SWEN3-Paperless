"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { locales, type Locale } from "./translations";

const I18nContext = createContext<{ locale: Locale; setLocale: (locale: Locale) => void; t: (key: string, vars?: Record<string, string | number>) => string } | null>(null);

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => {
    if (typeof window === "undefined") return "en";
    const stored = window.localStorage.getItem("paperless_locale") as Locale | null;
    return stored === "de" || stored === "en" ? stored : "en";
  });

  const setLocale = useCallback((value: Locale) => {
    window.localStorage.setItem("paperless_locale", value);
    setLocaleState(value);
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);
  const t = useCallback(
    (key: string, vars?: Record<string, string | number>) => {
      let text = locales[locale][key] ?? locales.en[key] ?? key;
      if (vars) {
        Object.entries(vars).forEach(([name, value]) => {
          text = text.replaceAll(`{${name}}`, String(value));
        });
      }
      return text;
    },
    [locale],
  );

  return <I18nContext.Provider value={{ locale, setLocale, t }}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const context = useContext(I18nContext);
  if (!context) throw new Error("useI18n must be used within I18nProvider");
  return context;
}
