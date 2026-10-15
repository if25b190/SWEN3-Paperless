const STORAGE_KEY = "paperless.token";
const listeners = new Set<() => void>();

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/** `undefined` means "not known yet" (server render / before hydration). */
export function getServerToken(): string | null | undefined {
  return undefined;
}

export function setToken(token: string) {
  try {
    window.localStorage.setItem(STORAGE_KEY, token);
  } catch {
    // ignore storage errors
  }
  listeners.forEach((listener) => listener());
}

export function clearToken() {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore storage errors
  }
  listeners.forEach((listener) => listener());
}

export function subscribeToken(listener: () => void) {
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
