"use client";

import { useState } from "react";
import {
  Alert,
  Button,
  List,
  ListItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import DeleteOutlineRounded from "@mui/icons-material/DeleteOutlineRounded";
import EditRounded from "@mui/icons-material/EditRounded";
import AddRounded from "@mui/icons-material/AddRounded";
import {
  ApiError,
  api,
  invalidParamFor,
  type DocumentType,
  type User,
} from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { describeError } from "../../lib/toast/errors";
import { useToast } from "../../lib/toast/ToastProvider";
import { Modal } from "../shared/Modal";

export function Settings({
  user,
  onUser,
  types,
  onTypes,
}: {
  user: User | null;
  onUser: (user: User) => void;
  types: DocumentType[];
  onTypes: (types: DocumentType[]) => void;
}) {
  const { t } = useI18n();
  const { toast } = useToast();

  const [addOpen, setAddOpen] = useState(false);
  const [addName, setAddName] = useState("");
  const [addDescription, setAddDescription] = useState("");
  const [addError, setAddError] = useState<string | null>(null);
  const [savingType, setSavingType] = useState(false);

  const [editFor, setEditFor] = useState<DocumentType | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editError, setEditError] = useState<string | null>(null);

  const [deleteFor, setDeleteFor] = useState<DocumentType | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);

  const refreshTypes = async () => {
    try {
      const page = await api.documentTypes();
      onTypes(page.items);
    } catch {
      /* keep the previous list */
    }
  };

  const createType = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingType(true);
    setAddError(null);
    try {
      await api.createDocumentType({ name: addName, description: addDescription || null });
      toast("success", t("messages.type_created"));
      setAddOpen(false);
      setAddName("");
      setAddDescription("");
      await refreshTypes();
    } catch (err) {
      setAddError(describeError(err, t));
    } finally {
      setSavingType(false);
    }
  };

  const openEdit = (type: DocumentType) => {
    setEditFor(type);
    setEditName(type.name);
    setEditDescription(type.description ?? "");
    setEditError(null);
  };

  const saveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editFor) return;
    setSavingType(true);
    setEditError(null);
    try {
      await api.updateDocumentType(editFor.id, {
        name: editName,
        description: editDescription || null,
      });
      toast("success", t("messages.type_updated"));
      setEditFor(null);
      await refreshTypes();
    } catch (err) {
      setEditError(describeError(err, t));
    } finally {
      setSavingType(false);
    }
  };

  const removeType = async () => {
    if (!deleteFor) return;
    setDeleting(true);
    try {
      await api.deleteDocumentType(deleteFor.id);
      toast("success", t("messages.type_deleted"));
      setDeleteFor(null);
      await refreshTypes();
    } catch (err) {
      toast("error", describeError(err, t));
      setDeleteFor(null);
    } finally {
      setDeleting(false);
    }
  };

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    setSavingProfile(true);
    setUsernameError(null);
    setPasswordError(null);
    const body: Partial<{ username: string; password: string }> = {};
    if (username) body.username = username;
    if (password) body.password = password;
    if (!username && !password) {
      setSavingProfile(false);
      return;
    }
    try {
      const updated = await api.updateUser(user.id, body);
      onUser(updated);
      setUsername("");
      setPassword("");
      toast("success", t("messages.profile_saved"));
    } catch (err) {
      if (err instanceof ApiError) {
        const usernameParam = invalidParamFor(err, "username");
        const passwordParam = invalidParamFor(err, "password");
        if (usernameParam) setUsernameError(usernameParam);
        if (passwordParam) setPasswordError(passwordParam);
        if (!usernameParam && !passwordParam) toast("error", err.message);
      } else {
        toast("error", t("common.error"));
      }
    } finally {
      setSavingProfile(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ borderRadius: 3, p: 2.5 }}>
        <Stack
          direction="row"
          sx={{ mb: 0.5, alignItems: "center", justifyContent: "space-between" }}
        >
          <Typography sx={{ fontWeight: 650 }}>{t("settings.document_types")}</Typography>
          <Button
            variant="outlined"
            size="small"
            startIcon={<AddRounded />}
            onClick={() => {
              setAddName("");
              setAddDescription("");
              setAddError(null);
              setAddOpen(true);
            }}
          >
            {t("settings.add_type")}
          </Button>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          {t("settings.types_hint")}
        </Typography>
        {types.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("settings.no_description")}
          </Typography>
        ) : (
          <List disablePadding>
            {types.map((type) => (
              <ListItem
                key={type.id}
                secondaryAction={
                  <Stack direction="row" spacing={0.5}>
                    <Button
                      size="small"
                      startIcon={<EditRounded />}
                      onClick={() => openEdit(type)}
                    >
                      {t("common.edit")}
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      startIcon={<DeleteOutlineRounded />}
                      onClick={() => setDeleteFor(type)}
                    >
                      {t("common.delete")}
                    </Button>
                  </Stack>
                }
                sx={{ px: 0, py: 1.25, borderBottom: "1px solid", borderColor: "divider", "&:last-child": { borderBottom: 0 } }}
              >
                <Stack spacing={0.25} sx={{ pr: 2 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {type.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {type.description ?? t("settings.no_description")}
                  </Typography>
                </Stack>
              </ListItem>
            ))}
          </List>
        )}
      </Paper>
      {user && (
        <Paper variant="outlined" sx={{ borderRadius: 3, p: 2.5 }}>
          <Typography sx={{ fontWeight: 650 }}>{t("settings.your_profile")}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
            {t("settings.profile_hint")}
          </Typography>
          <Stack component="form" spacing={2} onSubmit={saveProfile}>
            <TextField
              label={t("auth.username")}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              fullWidth
              size="small"
              error={!!usernameError}
              helperText={usernameError ?? undefined}
            />
            <TextField
              label={t("settings.new_password")}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              fullWidth
              size="small"
              helperText={passwordError ?? t("settings.password_hint")}
              error={!!passwordError}
            />
            <Stack sx={{ justifyContent: "flex-end" }}>
              <Button
                type="submit"
                variant="contained"
                disabled={savingProfile || (!username && !password)}
              >
                {t("common.save")}
              </Button>
            </Stack>
          </Stack>
        </Paper>
      )}
      {addOpen && (
        <Modal title={t("settings.add_type")} onClose={() => setAddOpen(false)}>
          <Stack component="form" spacing={2} onSubmit={createType}>
            <TextField
              label={t("settings.name")}
              value={addName}
              onChange={(e) => setAddName(e.target.value)}
              fullWidth
              size="small"
              required
            />
            <TextField
              label={t("settings.description")}
              value={addDescription}
              onChange={(e) => setAddDescription(e.target.value)}
              fullWidth
              size="small"
              multiline
              minRows={2}
            />
            {addError && <Alert severity="error">{addError}</Alert>}
            <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
              <Button onClick={() => setAddOpen(false)}>{t("common.cancel")}</Button>
              <Button type="submit" variant="contained" disabled={savingType || !addName.trim()}>
                {t("common.create")}
              </Button>
            </Stack>
          </Stack>
        </Modal>
      )}
      {editFor && (
        <Modal title={t("settings.edit_type")} onClose={() => setEditFor(null)}>
          <Stack component="form" spacing={2} onSubmit={saveEdit}>
            <TextField
              label={t("settings.name")}
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              fullWidth
              size="small"
              required
            />
            <TextField
              label={t("settings.description")}
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              fullWidth
              size="small"
              multiline
              minRows={2}
            />
            {editError && <Alert severity="error">{editError}</Alert>}
            <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
              <Button onClick={() => setEditFor(null)}>{t("common.cancel")}</Button>
              <Button type="submit" variant="contained" disabled={savingType || !editName.trim()}>
                {t("common.save")}
              </Button>
            </Stack>
          </Stack>
        </Modal>
      )}
      {deleteFor && (
        <Modal title={t("settings.delete_type")} onClose={() => setDeleteFor(null)}>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {t("settings.delete_type_confirm", { name: deleteFor.name })}
            </Typography>
            <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
              <Button onClick={() => setDeleteFor(null)}>{t("common.cancel")}</Button>
              <Button
                color="error"
                variant="contained"
                disabled={deleting}
                onClick={() => void removeType()}
              >
                {t("common.delete")}
              </Button>
            </Stack>
          </Stack>
        </Modal>
      )}
    </Stack>
  );
}
