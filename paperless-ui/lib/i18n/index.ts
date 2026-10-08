import en from "@/locales/en.json";
import de from "@/locales/de.json";

export type Locale = "en" | "de";
export const LOCALES: Locale[] = ["en", "de"];

// The German file must contain exactly the same keys as the English one.
const dictionaries: Record<Locale, typeof en> = { en, de };

const STORAGE_KEY = "paperless.locale";
const listeners = new Set<() => void>();

function isLocale(value: unknown): value is Locale {
  return value === "en" || value === "de";
}

function detectLocale(): Locale {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (isLocale(stored)) return stored;
  } catch {
    // storage unavailable, fall through to browser detection
  }
  return window.navigator.language?.toLowerCase().startsWith("de") ? "de" : "en";
}

export function getLocale(): Locale {
  if (typeof window === "undefined") return "en";
  return detectLocale();
}

export function getServerLocale(): Locale {
  return "en";
}

export function setLocale(locale: Locale) {
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // ignore storage errors
  }
  listeners.forEach((listener) => listener());
}

export function subscribeLocale(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function lookup(dictionary: unknown, key: string): string | undefined {
  let current: unknown = dictionary;
  for (const part of key.split(".")) {
    if (typeof current !== "object" || current === null) return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === "string" ? current : undefined;
}

export type TranslateParams = Record<string, string | number>;

export function translate(locale: Locale, key: string, params?: TranslateParams): string {
  const template = lookup(dictionaries[locale], key) ?? lookup(dictionaries.en, key) ?? key;
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

/** Translate using the currently active locale (usable outside React). */
export function t(key: string, params?: TranslateParams): string {
  return translate(getLocale(), key, params);
}
