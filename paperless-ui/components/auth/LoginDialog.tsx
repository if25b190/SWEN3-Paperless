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
import { api, type User } from "../../lib/api";
import { Modal } from "../shared/Modal";

export function LoginDialog({
  onClose,
  onLogin,
}: {
  onClose: () => void;
  onLogin: (u: User) => void;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    if (mode === "login") {
      try {
        const result = await api.login({ username, password });
        localStorage.setItem("paperless_token", result.token);
        onLogin(result.user);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Sign in failed.");
      } finally {
        setLoading(false);
      }
    } else {
      try {
        await api.register({ username, email, password });
        const result = await api.login({ username, password });
        localStorage.setItem("paperless_token", result.token);
        onLogin(result.user);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Registration failed.");
      } finally {
        setLoading(false);
      }
    }
  };

  return (
    <Modal
      title={mode === "login" ? "Welcome back." : "Create an account"}
      onClose={onClose}
    >
      <Tabs
        value={mode}
        onChange={(_, val) => {
          setMode(val);
          setError("");
        }}
        sx={{ mb: 2, borderBottom: 1, borderColor: "divider" }}
      >
        <Tab label="Sign in" value="login" />
        <Tab label="Register" value="register" />
      </Tabs>
      <Box component="form" onSubmit={submit}>
        <Stack sx={{ gap: 2, mt: 1 }}>
          <TextField
            label="Username"
            required
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          {mode === "register" && (
            <TextField
              label="Email"
              required
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
          <TextField
            label="Password"
            required
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
        </Stack>
        <DialogActions sx={{ px: 0, mt: 3, justifyContent: "space-between" }}>
          <Button
            type="button"
            variant="text"
            size="small"
            onClick={() => {
              setMode(mode === "login" ? "register" : "login");
              setError("");
            }}
          >
            {mode === "login"
              ? "Need an account? Register"
              : "Already have an account? Sign in"}
          </Button>
          <Box sx={{ display: "flex", gap: 1 }}>
            <Button type="button" variant="outlined" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={loading}>
              {mode === "login"
                ? loading
                  ? "Signing in…"
                  : "Sign in"
                : loading
                  ? "Registering…"
                  : "Register"}
            </Button>
          </Box>
        </DialogActions>
      </Box>
    </Modal>
  );
}
