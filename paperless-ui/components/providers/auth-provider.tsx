"use client";

import { createContext, useCallback, useContext, useMemo, useState, useSyncExternalStore } from "react";
import { authApi } from "@/lib/api/services";
import { clearToken, getServerToken, getToken, setToken, subscribeToken } from "@/lib/auth-token";
import { useQuery } from "@/lib/use-query";
import type { User } from "@/lib/types";

interface AuthContextValue {
  /** True once the token store and /auth/me have been resolved. */
  ready: boolean;
  user: User | null;
  token: string | null;
  login: (username: string, password: string) => Promise<User>;
  logout: () => void;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const token = useSyncExternalStore(subscribeToken, getToken, getServerToken);
  const [override, setOverride] = useState<{ token: string; user: User } | null>(null);

  const overrideUser = override && override.token === token ? override.user : null;
  const me = useQuery(token && !overrideUser ? `me:${token}` : null, () =>
    authApi.me({ silent: false }),
  );

  const user = overrideUser ?? me.data ?? null;
  const ready = token !== undefined && (token === null || user !== null || me.error !== undefined);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(
      { username, password },
      { anonymous: true, messages: { 401: "errors.invalid_credentials" } },
    );
    setOverride({ token: response.token, user: response.user });
    setToken(response.token);
    return response.user;
  }, []);

  const logout = useCallback(() => {
    setOverride(null);
    clearToken();
  }, []);

  const setUser = useCallback(
    (next: User) => {
      if (token) setOverride({ token, user: next });
    },
    [token],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      ready,
      user: token ? user : null,
      token: token ?? null,
      login,
      logout,
      setUser,
    }),
    [ready, user, token, login, logout, setUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
