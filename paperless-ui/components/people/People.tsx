"use client";

import { useMemo, useState } from "react";
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import ArrowBackIosRounded from "@mui/icons-material/ArrowBackIosRounded";
import DeleteOutlineRounded from "@mui/icons-material/DeleteOutlineRounded";
import EditRounded from "@mui/icons-material/EditRounded";
import PersonAddAlt1Rounded from "@mui/icons-material/PersonAddAlt1Rounded";
import {
  api,
  type Member,
  type Role,
  type Team,
  type TeamMembership,
  type User,
} from "../../lib/api";
import { useI18n } from "../../lib/i18n/I18nProvider";
import { describeError } from "../../lib/toast/errors";
import { useToast } from "../../lib/toast/ToastProvider";
import { Modal } from "../shared/Modal";

const roles: Role[] = ["ADMIN", "READ_WRITE", "READONLY"];

const roleBadge = {
  borderRadius: 1,
  fontSize: 11,
  fontWeight: 600,
  height: 22,
};

const initials = (name = "") =>
  name
    .split(/[ _-]/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

const formatDate = (value: string | undefined, locale: string) =>
  value
    ? new Date(value).toLocaleDateString(locale === "de" ? "de-DE" : "en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      })
    : "—";

function Field({ label, value }: { label: string; value: string }) {
  return (
    <Stack spacing={0.25}>
      <Typography variant="caption" sx={{ color: "text.secondary" }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {value}
      </Typography>
    </Stack>
  );
}

export function People({
  user,
  teams,
  memberships,
  onTeams,
  onMemberships,
}: {
  user: User | null;
  teams: Team[];
  memberships: TeamMembership[];
  onTeams: (teams: Team[]) => void;
  onMemberships: (memberships: TeamMembership[]) => void;
}) {
  const { t, locale } = useI18n();
  const { toast } = useToast();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<Team | null>(null);
  const [members, setMembers] = useState<Member[] | null>(null);
  const [ownerName, setOwnerName] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [createError, setCreateError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [editOpen, setEditOpen] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editError, setEditError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const [addOpen, setAddOpen] = useState(false);
  const [addQuery, setAddQuery] = useState("");
  const [addUser, setAddUser] = useState<User | null>(null);
  const [addRole, setAddRole] = useState<Role>("READ_WRITE");
  const [addError, setAddError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [allUsers, setAllUsers] = useState<User[] | null>(null);

  const [roleFor, setRoleFor] = useState<Member | null>(null);
  const [roleValue, setRoleValue] = useState<Role>("READONLY");
  const [roleError, setRoleError] = useState<string | null>(null);
  const [roleSaving, setRoleSaving] = useState(false);

  const [removeFor, setRemoveFor] = useState<Member | null>(null);
  const [removing, setRemoving] = useState(false);

  const selectedTeam = detail ?? teams.find((team) => team.id === selectedId) ?? null;
  const ownRole = selectedTeam
    ? memberships.find((m) => m.team.id === selectedTeam.id)?.role ?? null
    : null;
  const isOwnerOf = (team: Team) => user?.id === team.owner_id;
  const canManage = (team: Team) => ownRole === "ADMIN" || isOwnerOf(team);

  const openTeam = (team: Team) => {
    setSelectedId(team.id);
    setDetail(team);
    setMembers(null);
    setOwnerName(isOwnerOf(team) ? (user?.username ?? null) : null);
    void api
      .members(team.id)
      .then((page) => setMembers(page.items))
      .catch(() => setMembers([]));
    if (user && !isOwnerOf(team)) {
      api
        .user(team.owner_id)
        .then((u) => setOwnerName(u.username))
        .catch(() => setOwnerName(null));
    }
  };

  const closeTeam = () => {
    setSelectedId(null);
    setDetail(null);
    setMembers(null);
    setOwnerName(null);
  };

  const reload = async () => {
    if (!user) return;
    const [teamsPage, membershipPage] = await Promise.all([
      api.teams(),
      api.userTeams(user.id),
    ]);
    onTeams(teamsPage.items);
    onMemberships(membershipPage.items);
    if (selectedId) {
      const [freshDetail, memberPage] = await Promise.all([
        api.team(selectedId),
        api.members(selectedId),
      ]);
      setDetail(freshDetail);
      setMembers(memberPage.items);
      setOwnerName(
        isOwnerOf(freshDetail)
          ? (user.username ?? null)
          : await api
              .user(freshDetail.owner_id)
              .then((u) => u.username)
              .catch(() => null),
      );
    }
  };

  const createTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError(null);
    try {
      await api.createTeam({
        name: createName,
        description: createDescription || null,
      });
      toast("success", t("messages.team_created"));
      setCreateOpen(false);
      setCreateName("");
      setCreateDescription("");
      await reload();
    } catch (err) {
      setCreateError(describeError(err, t));
    } finally {
      setCreating(false);
    }
  };

  const openEdit = () => {
    if (!selectedTeam) return;
    setEditName(selectedTeam.name);
    setEditDescription(selectedTeam.description ?? "");
    setEditError(null);
    setEditOpen(true);
  };

  const saveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTeam) return;
    setEditing(true);
    setEditError(null);
    try {
      await api.updateTeam(selectedTeam.id, {
        name: editName,
        description: editDescription || null,
      });
      toast("success", t("messages.team_updated"));
      setEditOpen(false);
      await reload();
    } catch (err) {
      setEditError(describeError(err, t));
    } finally {
      setEditing(false);
    }
  };

  const removeTeam = async () => {
    if (!selectedTeam) return;
    setDeleting(true);
    try {
      await api.deleteTeam(selectedTeam.id);
      toast("success", t("messages.team_deleted"));
      setDeleteOpen(false);
      closeTeam();
      await reload();
    } catch (err) {
      toast("error", describeError(err, t));
      setDeleteOpen(false);
    } finally {
      setDeleting(false);
    }
  };

  const openAdd = () => {
    setAddQuery("");
    setAddUser(null);
    setAddRole("READ_WRITE");
    setAddError(null);
    setAddOpen(true);
    if (allUsers === null) {
      api
        .users()
        .then((page) => setAllUsers(page.items))
        .catch(() => setAllUsers([]));
    }
  };

  const candidates = useMemo(() => {
    if (allUsers === null) return [];
    const memberIds = new Set((members ?? []).map((m) => m.user.id));
    return allUsers.filter(
      (u) =>
        !memberIds.has(u.id) &&
        (addQuery === "" || u.username.toLowerCase().includes(addQuery.toLowerCase())),
    );
  }, [allUsers, members, addQuery]);

  const addMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTeam || !addUser) return;
    setAdding(true);
    setAddError(null);
    try {
      await api.addMember(selectedTeam.id, { user_id: addUser.id, role: addRole });
      toast("success", t("messages.member_added"));
      setAddOpen(false);
      await reload();
    } catch (err) {
      setAddError(describeError(err, t));
    } finally {
      setAdding(false);
    }
  };

  const openRole = (member: Member) => {
    setRoleFor(member);
    setRoleValue(member.role);
    setRoleError(null);
  };

  const saveRole = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTeam || !roleFor) return;
    setRoleSaving(true);
    setRoleError(null);
    try {
      await api.updateMember(selectedTeam.id, roleFor.user.id, roleValue);
      toast("success", t("messages.role_updated"));
      setRoleFor(null);
      await reload();
    } catch (err) {
      setRoleError(describeError(err, t));
    } finally {
      setRoleSaving(false);
    }
  };

  const removeMember = async () => {
    if (!selectedTeam || !removeFor) return;
    setRemoving(true);
    try {
      await api.removeMember(selectedTeam.id, removeFor.user.id);
      toast("success", t("messages.member_removed"));
      setRemoveFor(null);
      await reload();
    } catch (err) {
      toast("error", describeError(err, t));
      setRemoveFor(null);
    } finally {
      setRemoving(false);
    }
  };

  if (selectedTeam) {
    const manageable = canManage(selectedTeam);
    return (
      <Stack spacing={3}>
        <Button
          size="small"
          startIcon={<ArrowBackIosRounded />}
          onClick={closeTeam}
          sx={{ alignSelf: "flex-start", ml: -1 }}
        >
          {t("teams.back")}
        </Button>
        <Paper variant="outlined" sx={{ borderRadius: 3, p: 2.5 }}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={2}
            sx={{
              alignItems: { xs: "flex-start", sm: "center" },
              justifyContent: "space-between",
            }}
          >
            <Stack spacing={0.5} sx={{ minWidth: 0 }}>
              <Typography sx={{ fontWeight: 700, fontSize: 20 }} noWrap>
                {selectedTeam.name}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {selectedTeam.description ?? t("teams.no_description")}
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
              {manageable && (
                <Button variant="outlined" size="small" startIcon={<EditRounded />} onClick={openEdit}>
                  {t("teams.edit_team")}
                </Button>
              )}
              {isOwnerOf(selectedTeam) && (
                <Button
                  variant="outlined"
                  size="small"
                  color="error"
                  startIcon={<DeleteOutlineRounded />}
                  onClick={() => setDeleteOpen(true)}
                >
                  {t("teams.delete_team")}
                </Button>
              )}
            </Stack>
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={3} sx={{ mt: 2.5 }}>
            <Field label={t("teams.owner")} value={ownerName ?? t("common.loading")} />
            <Field label={t("teams.created")} value={formatDate(selectedTeam.created_at, locale)} />
            {ownRole && (
              <Stack spacing={0.25}>
                <Typography variant="caption" sx={{ color: "text.secondary" }}>
                  {t("teams.role")}
                </Typography>
                <Chip label={t(`roles.${ownRole}`)} size="small" color="secondary" sx={roleBadge} />
              </Stack>
            )}
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ borderRadius: 3, p: 2.5 }}>
          <Stack
            direction="row"
            sx={{ mb: 1.5, alignItems: "center", justifyContent: "space-between" }}
          >
            <Typography sx={{ fontWeight: 650 }}>{t("teams.members")}</Typography>
            {manageable && (
              <Button variant="outlined" size="small" startIcon={<PersonAddAlt1Rounded />} onClick={openAdd}>
                {t("teams.add_member")}
              </Button>
            )}
          </Stack>
          {members === null ? (
            <Typography variant="body2" color="text.secondary" sx={{ px: 1 }}>
              {t("common.loading")}
            </Typography>
          ) : members.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ px: 1 }}>
              {t("teams.no_members")}
            </Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t("teams.member")}</TableCell>
                  <TableCell>{t("teams.role")}</TableCell>
                  <TableCell>{t("teams.joined")}</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {members.map((member) => {
                  const protectedRow = user?.id === member.user.id && isOwnerOf(selectedTeam);
                  return (
                    <TableRow key={member.user.id} hover>
                      <TableCell>
                        <Stack direction="row" spacing={1.25} sx={{ alignItems: "center" }}>
                          <Avatar
                            sx={{
                              width: 28,
                              height: 28,
                              fontSize: 11,
                              fontWeight: 700,
                              bgcolor: "#d8ef83",
                              color: "#183729",
                            }}
                          >
                            {initials(member.user.username)}
                          </Avatar>
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                              {member.user.username}
                            </Typography>
                            {protectedRow && (
                              <Typography variant="caption" color="text.secondary">
                                {t("teams.owner")}
                              </Typography>
                            )}
                          </Box>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={t(`roles.${member.role}`)}
                          size="small"
                          color={member.role === "ADMIN" ? "primary" : "default"}
                          sx={roleBadge}
                        />
                      </TableCell>
                      <TableCell>{formatDate(member.joined_at, locale)}</TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.5} sx={{ justifyContent: "flex-end" }}>
                          <Tooltip title={protectedRow ? t("teams.owner_tooltip") : undefined}>
                            <span>
                              <Button
                                size="small"
                                disabled={protectedRow}
                                onClick={() => openRole(member)}
                              >
                                {t("teams.change_role")}
                              </Button>
                            </span>
                          </Tooltip>
                          <Tooltip title={protectedRow ? t("teams.owner_tooltip") : undefined}>
                            <span>
                              <Button
                                size="small"
                                color="error"
                                disabled={protectedRow}
                                onClick={() => setRemoveFor(member)}
                              >
                                {t("teams.remove_member")}
                              </Button>
                            </span>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Paper>
        {editOpen && (
          <Modal title={t("teams.edit_team")} onClose={() => setEditOpen(false)}>
            <Stack component="form" spacing={2} onSubmit={saveEdit}>
              <TextField
                label={t("teams.name")}
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                fullWidth
                size="small"
                required
              />
              <TextField
                label={t("teams.description")}
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                fullWidth
                size="small"
                multiline
                minRows={2}
              />
              {editError && <Alert severity="error">{editError}</Alert>}
              <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                <Button onClick={() => setEditOpen(false)}>{t("common.cancel")}</Button>
                <Button type="submit" variant="contained" disabled={editing || !editName.trim()}>
                  {t("common.save")}
                </Button>
              </Stack>
            </Stack>
          </Modal>
        )}
        {deleteOpen && (
          <Modal title={t("teams.delete_team")} onClose={() => setDeleteOpen(false)}>
            <Stack spacing={2}>
              <Typography variant="body2" color="text.secondary">
                {t("teams.delete_team_warning", { team: selectedTeam.name })}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                <Button onClick={() => setDeleteOpen(false)}>{t("common.cancel")}</Button>
                <Button
                  color="error"
                  variant="contained"
                  disabled={deleting}
                  onClick={() => void removeTeam()}
                >
                  {t("common.delete")}
                </Button>
              </Stack>
            </Stack>
          </Modal>
        )}
        {addOpen && (
          <Modal title={t("teams.add_member_title", { team: selectedTeam.name })} onClose={() => setAddOpen(false)}>
            <Stack component="form" spacing={2} onSubmit={addMember}>
              <TextField
                label={t("teams.search_users")}
                value={addQuery}
                onChange={(e) => {
                  setAddQuery(e.target.value);
                  setAddUser(null);
                }}
                fullWidth
                size="small"
              />
              <Box
                sx={{
                  border: "1px solid",
                  borderColor: "divider",
                  borderRadius: 2,
                  maxHeight: 240,
                  overflow: "auto",
                }}
              >
                <List dense disablePadding>
                  {candidates.length === 0 ? (
                    <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2 }}>
                      {t("teams.select_user")}
                    </Typography>
                  ) : (
                    candidates.map((candidate) => (
                      <ListItemButton
                        key={candidate.id}
                        selected={addUser?.id === candidate.id}
                        onClick={() => setAddUser(candidate)}
                      >
                        <ListItemText
                          primary={candidate.username}
                          slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 13.5 } } }}
                        />
                      </ListItemButton>
                    ))
                  )}
                </List>
              </Box>
              <TextField
                select
                label={t("teams.role")}
                value={addRole}
                onChange={(e) => setAddRole(e.target.value as Role)}
                fullWidth
                size="small"
              >
                {roles.map((role) => (
                  <MenuItem key={role} value={role}>
                    {t(`roles.${role}`)}
                  </MenuItem>
                ))}
              </TextField>
              {addError && <Alert severity="error">{addError}</Alert>}
              <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                <Button onClick={() => setAddOpen(false)}>{t("common.cancel")}</Button>
                <Button type="submit" variant="contained" disabled={adding || !addUser}>
                  {t("common.create")}
                </Button>
              </Stack>
            </Stack>
          </Modal>
        )}
        {roleFor && (
          <Modal
            title={t("teams.change_role_title", { user: roleFor.user.username })}
            onClose={() => setRoleFor(null)}
          >
            <Stack component="form" spacing={2} onSubmit={saveRole}>
              <TextField
                select
                label={t("teams.role")}
                value={roleValue}
                onChange={(e) => setRoleValue(e.target.value as Role)}
                fullWidth
                size="small"
              >
                {roles.map((role) => (
                  <MenuItem key={role} value={role}>
                    {t(`roles.${role}`)}
                  </MenuItem>
                ))}
              </TextField>
              {roleError && <Alert severity="error">{roleError}</Alert>}
              <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                <Button onClick={() => setRoleFor(null)}>{t("common.cancel")}</Button>
                <Button type="submit" variant="contained" disabled={roleSaving}>
                  {t("common.save")}
                </Button>
              </Stack>
            </Stack>
          </Modal>
        )}
        {removeFor && (
          <Modal
            title={t("teams.remove_title", {
              user: removeFor.user.username,
              team: selectedTeam?.name ?? "",
            })}
            onClose={() => setRemoveFor(null)}
          >
            <Stack spacing={2}>
              <Typography variant="body2" color="text.secondary">
                {t("teams.remove_warning")}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                <Button onClick={() => setRemoveFor(null)}>{t("common.cancel")}</Button>
                <Button
                  color="error"
                  variant="contained"
                  disabled={removing}
                  onClick={() => void removeMember()}
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

  return (
    <Stack spacing={3}>
      <Stack direction="row" sx={{ justifyContent: "flex-end" }}>
        <Button variant="contained" startIcon={<PersonAddAlt1Rounded />} onClick={() => setCreateOpen(true)}>
          {t("teams.create")}
        </Button>
      </Stack>
      {teams.length === 0 ? (
        <Alert severity="info">{t("teams.empty")}</Alert>
      ) : (
        <Box
          sx={{
            display: "grid",
            gap: 2,
            gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", lg: "repeat(3, 1fr)" },
          }}
        >
          {teams.map((team) => {
            const role =
              memberships.find((m) => m.team.id === team.id)?.role ??
              (user?.id === team.owner_id ? "ADMIN" : null);
            return (
              <Paper
                key={team.id}
                variant="outlined"
                sx={{
                  borderRadius: 3,
                  p: 2.5,
                  cursor: "pointer",
                  transition: "box-shadow 0.2s",
                  "&:hover": { boxShadow: "0 6px 18px rgba(15, 43, 31, 0.12)" },
                }}
                onClick={() => openTeam(team)}
              >
                <Stack direction="row" sx={{ mb: 1, gap: 1, justifyContent: "space-between" }}>
                  <Typography sx={{ fontWeight: 650, fontSize: 16 }} noWrap>
                    {team.name}
                  </Typography>
                  {role && (
                    <Chip
                      label={t(`roles.${role}`)}
                      size="small"
                      color="secondary"
                      sx={roleBadge}
                    />
                  )}
                </Stack>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{
                    minHeight: 40,
                    display: "-webkit-box",
                    overflow: "hidden",
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: "vertical",
                  }}
                >
                  {team.description ?? t("teams.no_description")}
                </Typography>
                {role && (
                  <Typography variant="caption" sx={{ color: "text.secondary", display: "block", mt: 1 }}>
                    {t("teams.role")}: {t(`roles.${role}`)}
                  </Typography>
                )}
              </Paper>
            );
          })}
        </Box>
      )}
      {createOpen && (
        <Modal title={t("teams.create_team")} onClose={() => setCreateOpen(false)}>
          <Stack component="form" spacing={2} onSubmit={createTeam}>
            <TextField
              label={t("teams.name")}
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              fullWidth
              size="small"
              required
            />
            <TextField
              label={t("teams.description")}
              value={createDescription}
              onChange={(e) => setCreateDescription(e.target.value)}
              fullWidth
              size="small"
              multiline
              minRows={2}
            />
            {createError && <Alert severity="error">{createError}</Alert>}
            <Stack direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
              <Button onClick={() => setCreateOpen(false)}>{t("common.cancel")}</Button>
              <Button type="submit" variant="contained" disabled={creating || !createName.trim()}>
                {t("common.create")}
              </Button>
            </Stack>
          </Stack>
        </Modal>
      )}
    </Stack>
  );
}
