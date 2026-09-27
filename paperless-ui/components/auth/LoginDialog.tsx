"use client";

import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  DialogActions,
  Stack,
  Tab,
  Tabs,
  TextField,
} from "@mui/material";
import { api, ApiError, invalidParamFor, type User } from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { Modal } from "../shared/Modal";

type FieldErrors = { username?: string; password?: string };

export function LoginDialog({
  onClose,
  onLogin,
}: {
  onClose: () => void;
  onLogin: (user: User) => void;
}) {
  const { t } = useI18n();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loading, setLoading] = useState(false);

  const switchMode = (next: "login" | "register") => {
    setMode(next);
    setError("");
    setNotice("");
    setFieldErrors({});
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setNotice("");
    setFieldErrors({});

    const next: FieldErrors = {};
    if (!username.trim()) next.username = t("validation.required");
    if (!password) next.password = t("validation.required");
    if (mode === "register") {
      if (username.length < 4) next.username = t("validation.username_short");
      else if (username.length > 50) next.username = t("validation.username_long");
      if (password.length < 8) next.password = t("validation.password_short");
      else if (password.length > 100) next.password = t("validation.password_long");
    }
    if (next.username || next.password) {
      setFieldErrors(next);
      return;
    }

    setLoading(true);
    try {
      if (mode === "login") {
        const result = await api.login({ username, password });
        localStorage.setItem("paperless_token", result.token);
        onLogin(result.user);
      } else {
        const created = await api.register({ username, password });
        setNotice(t("auth.register_success"));
        setMode("login");
        setUsername(created.username);
        setPassword("");
      }
    } catch (err) {
      if (err instanceof ApiError) {
        const usernameError = invalidParamFor(err, "username");
        const passwordError = invalidParamFor(err, "password");
        if (usernameError || passwordError) {
          setFieldErrors({ username: usernameError ?? undefined, password: passwordError ?? undefined });
          setLoading(false);
          return;
        }
      }
      setError(err instanceof Error ? err.message : mode === "login" ? t("auth.sign_in_failed") : t("auth.register_failed"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={mode === "login" ? t("auth.welcome_back") : t("auth.create_account")}
      onClose={onClose}
    >
      <Tabs
        value={mode}
        onChange={(_, value) => switchMode(value)}
        aria-label={t("auth.account")}
      >
        <Tab label={t("auth.sign_in")} value="login" />
        <Tab label={t("auth.register")} value="register" />
      </Tabs>
      <Box component="form" onSubmit={submit} noValidate>
        {notice && <Alert severity="success" sx={{ mb: 2 }}>{notice}</Alert>}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Stack spacing={2}>
          <TextField
            label={t("auth.username")}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            error={Boolean(fieldErrors.username)}
            helperText={fieldErrors.username}
            autoComplete="username"
            autoFocus
          />
          <TextField
            label={t("auth.password")}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={Boolean(fieldErrors.password)}
            helperText={fieldErrors.password}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </Stack>
        <DialogActions sx={{ mt: 3, justifyContent: "space-between" }}>
          <Button
            type="button"
            color="inherit"
            onClick={() => switchMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? t("auth.need_account") : t("auth.have_account")}
          </Button>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? (mode === "login" ? t("auth.signing_in") : t("auth.registering")) : mode === "login" ? t("auth.sign_in") : t("auth.register")}
          </Button>
        </DialogActions>
      </Box>
    </Modal>
  );
}
