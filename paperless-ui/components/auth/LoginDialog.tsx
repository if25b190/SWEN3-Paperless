import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  DialogActions,
  Stack,
  TextField,
} from "@mui/material";
import { api, type User } from "../../lib/api";
import { Modal } from "../shared/Modal";

export function LoginDialog({
  onClose,
  onLogin,
  onError,
}: {
  onClose: () => void;
  onLogin: (u: User) => void;
  onError: (e: unknown, f: string) => void;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const result = await api.login({ username, password });
      localStorage.setItem("paperless_token", result.token);
      onLogin(result.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign in failed.");
      onError(err, "Sign in failed.");
    }
  };
  return (
    <Modal title="Welcome back." onClose={onClose}>
      <Box component="form" onSubmit={submit}>
        <Stack sx={{ gap: 2, mt: 1 }}>
          <TextField
            label="Username"
            required
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
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
        <DialogActions sx={{ px: 0, mt: 3 }}>
          <Button type="button" variant="outlined" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="contained">
            Sign in
          </Button>
        </DialogActions>
      </Box>
    </Modal>
  );
}
