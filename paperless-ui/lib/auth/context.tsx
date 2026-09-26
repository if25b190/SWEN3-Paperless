"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { UserResponse, LoginRequest, CreateUserRequest } from "../types/api";
import { authApi, usersApi, getToken, setToken, clearToken } from "../api/client";
import { toast } from "sonner";
import { useTranslation } from "../i18n/context";

interface AuthContextType {
  user: UserResponse | null;
  loading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: CreateUserRequest) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const { t } = useTranslation();

  const refreshUser = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const currentUser = await authApi.me(false);
      setUser(currentUser);
    } catch {
      clearToken();
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const login = async (data: LoginRequest) => {
    const response = await authApi.login(data);
    setToken(response.token);
    setUser(response.user);
    toast.success(t.auth.loginSuccess);
    router.push("/dashboard");
  };

  const register = async (data: CreateUserRequest) => {
    await usersApi.register(data);
    toast.success(t.auth.registerSuccess);
    // Automatically log in after registration
    await login({ username: data.username, password: data.password });
  };

  const logout = () => {
    clearToken();
    setUser(null);
    toast.info("Signed out");
    router.push("/login");
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        register,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
