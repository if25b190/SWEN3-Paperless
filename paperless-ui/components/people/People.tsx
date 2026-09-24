import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  DialogActions,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { api, type Member, type Team, type User } from "../../lib/api";
import { Modal } from "../shared/Modal";
import { sectionSx } from "../shared/styles";

export function People({
  users,
  teams,
  onUsers,
  onTeams,
  onError,
}: {
  users: User[];
  teams: Team[];
  onUsers: (v: User[]) => void;
  onTeams: (v: Team[]) => void;
  onError: (e: unknown, f: string) => void;
}) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [teamName, setTeamName] = useState("");
  const [members, setMembers] = useState<Record<number, Member[]>>({});
  const [memberId, setMemberId] = useState<Record<number, string>>({});
  const [roles, setRoles] = useState<Record<number, Member["role"]>>({});
  const [editingUser, setEditingUser] = useState<{
    id: number;
    username: string;
    email: string;
    error?: string;
  } | null>(null);
  const [membershipsModal, setMembershipsModal] = useState<{
    user: User;
    items: { team: Team; role: Member["role"] }[];
    loading: boolean;
    error?: string;
  } | null>(null);
  const [editingTeam, setEditingTeam] = useState<{
    id: number;
    name: string;
    description: string;
    error?: string;
  } | null>(null);
  const createUser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      onUsers([
        ...users,
        await api.createUser({ username: name, email, password }),
      ]);
      setName("");
      setEmail("");
      setPassword("");
    } catch (err) {
      onError(err, "User could not be created.");
    }
  };
  const createTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      onTeams([...teams, await api.createTeam({ name: teamName })]);
      setTeamName("");
    } catch (err) {
      onError(err, "Team could not be created.");
    }
  };
  const loadMembers = async (id: number) => {
    try {
      const result = await api.members(id);
      setMembers((current) => ({ ...current, [id]: result.items }));
    } catch (err) {
      onError(err, "Members could not be loaded.");
    }
  };
  const addMember = async (id: number) => {
    const userId = memberId[id];
    if (!userId) return;
    try {
      const member = await api.addMember(id, {
        user_id: Number(userId),
        role: roles[id] || "MEMBER",
      });
      setMembers((current) => ({
        ...current,
        [id]: [...(current[id] || []), member],
      }));
      setMemberId((current) => ({ ...current, [id]: "" }));
    } catch (err) {
      onError(err, "Member could not be added.");
    }
  };
  const updateMember = async (
    teamId: number,
    userId: number,
    nextRole: Member["role"],
  ) => {
    try {
      const updated = await api.updateMember(teamId, userId, nextRole);
      setMembers((current) => ({
        ...current,
        [teamId]: (current[teamId] || []).map((member) =>
          member.user.id === userId ? updated : member,
        ),
      }));
    } catch (err) {
      onError(err, "Role could not be updated.");
    }
  };
  const removeMember = async (teamId: number, userId: number) => {
    try {
      await api.removeMember(teamId, userId);
      setMembers((current) => ({
        ...current,
        [teamId]: (current[teamId] || []).filter(
          (member) => member.user.id !== userId,
        ),
      }));
    } catch (err) {
      onError(err, "Member could not be removed.");
    }
  };
  return (
    <Box
      className="reveal"
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr", lg: "repeat(2,minmax(0,1fr))" },
        gap: 3,
      }}
    >
      <Card component="section" sx={sectionSx}>
        <Typography component="h2" variant="h5">
          Members
        </Typography>
        <Stack component="form" onSubmit={createUser} sx={{ gap: 1.5, mt: 3 }}>
          <TextField
            label="Username"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <TextField
            label="Email"
            required
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <TextField
            label="Password"
            required
            slotProps={{ htmlInput: { minLength: 6 } }}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button
            type="submit"
            variant="contained"
            sx={{ alignSelf: "flex-start" }}
          >
            Add user
          </Button>
        </Stack>
        <Stack sx={{ gap: 1, mt: 3 }}>
          {users.map((u) => (
            <Paper
              key={u.id}
              variant="outlined"
              sx={{
                p: 1.5,
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: 1,
                bgcolor: "action.hover",
              }}
            >
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {u.username}
              </Typography>
              <Stack direction="row" sx={{ flexWrap: "wrap" }}>
                <Button
                  size="small"
                  onClick={() =>
                    api
                      .user(u.id)
                      .then((detail) => {
                        setEditingUser({
                          id: u.id,
                          username: detail.username,
                          email: detail.email || "",
                        });
                      })
                      .catch((err) =>
                        onError(err, "User details could not be loaded."),
                      )
                  }
                >
                  Edit
                </Button>
                <Button
                  size="small"
                  onClick={() => {
                    setMembershipsModal({ user: u, items: [], loading: true });
                    api
                      .userTeams(u.id)
                      .then((result) => {
                        setMembershipsModal({
                          user: u,
                          items: result.items,
                          loading: false,
                        });
                      })
                      .catch((err) => {
                        setMembershipsModal({
                          user: u,
                          items: [],
                          loading: false,
                          error:
                            err instanceof Error
                              ? err.message
                              : "Memberships could not be loaded.",
                        });
                      });
                  }}
                >
                  Memberships
                </Button>
                <Button
                  size="small"
                  color="error"
                  onClick={() =>
                    api
                      .deleteUser(u.id)
                      .then(() => onUsers(users.filter((x) => x.id !== u.id)))
                      .catch((err) =>
                        onError(err, "User could not be deleted."),
                      )
                  }
                >
                  Delete
                </Button>
              </Stack>
            </Paper>
          ))}
        </Stack>
      </Card>
      <Card component="section" sx={sectionSx}>
        <Typography component="h2" variant="h5">
          Teams
        </Typography>
        <Stack
          component="form"
          onSubmit={createTeam}
          direction="row"
          sx={{ gap: 1, flexWrap: "wrap", mt: 3 }}
        >
          <TextField
            label="Team name"
            required
            value={teamName}
            onChange={(e) => setTeamName(e.target.value)}
            sx={{ flex: 1, minWidth: 160 }}
          />
          <Button type="submit" variant="contained">
            Add team
          </Button>
        </Stack>
        <Stack sx={{ gap: 1, mt: 3 }}>
          {teams.map((team) => (
            <Paper
              key={team.id}
              data-team-id={team.id}
              variant="outlined"
              sx={{ p: 2, bgcolor: "action.hover" }}
            >
              <Stack
                direction="row"
                sx={{
                  gap: 1,
                  alignItems: "center",
                  justifyContent: "space-between",
                  flexWrap: "wrap",
                }}
              >
                <Typography sx={{ fontWeight: 600 }}>{team.name}</Typography>
                <Stack direction="row" sx={{ flexWrap: "wrap" }}>
                  <Button
                    size="small"
                    onClick={() =>
                      api
                        .team(team.id)
                        .then((detail) => {
                          setEditingTeam({
                            id: team.id,
                            name: detail.name,
                            description: detail.description || "",
                          });
                        })
                        .catch((err) =>
                          onError(err, "Team details could not be loaded."),
                        )
                    }
                  >
                    Edit
                  </Button>
                  <Button
                    size="small"
                    onClick={() => void loadMembers(team.id)}
                  >
                    Members
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    onClick={() =>
                      api
                        .deleteTeam(team.id)
                        .then(() =>
                          onTeams(teams.filter((x) => x.id !== team.id)),
                        )
                        .catch((err) =>
                          onError(err, "Team could not be deleted."),
                        )
                    }
                  >
                    Delete
                  </Button>
                </Stack>
              </Stack>
              {members[team.id] && (
                <Stack
                  sx={{
                    gap: 1.5,
                    mt: 2,
                    pt: 2,
                    borderTop: "1px solid",
                    borderColor: "divider",
                  }}
                >
                  {members[team.id].map((member) => (
                    <Stack
                      key={member.user.id}
                      direction="row"
                      sx={{
                        gap: 1,
                        alignItems: "center",
                        justifyContent: "space-between",
                        flexWrap: "wrap",
                      }}
                    >
                      <Typography variant="body2">
                        {member.user.username}
                      </Typography>
                      <Stack
                        direction="row"
                        sx={{ gap: 1, alignItems: "center" }}
                      >
                        <FormControl size="small" sx={{ minWidth: 165 }}>
                          <InputLabel
                            id={`member-role-${team.id}-${member.user.id}`}
                            shrink
                          >{`Role for ${member.user.username}`}</InputLabel>
                          <Select
                            labelId={`member-role-${team.id}-${member.user.id}`}
                            label={`Role for ${member.user.username}`}
                            value={member.role}
                            onChange={(e) =>
                              void updateMember(
                                team.id,
                                member.user.id,
                                e.target.value as Member["role"],
                              )
                            }
                          >
                            <MenuItem value="ADMIN">ADMIN</MenuItem>
                            <MenuItem value="READONLY">READONLY</MenuItem>
                            <MenuItem value="MEMBER">MEMBER</MenuItem>
                          </Select>
                        </FormControl>
                        <Button
                          size="small"
                          color="error"
                          onClick={() =>
                            void removeMember(team.id, member.user.id)
                          }
                        >
                          Remove
                        </Button>
                      </Stack>
                    </Stack>
                  ))}
                  <Stack
                    direction="row"
                    sx={{ gap: 1, alignItems: "center", flexWrap: "wrap" }}
                  >
                    <FormControl size="small" sx={{ minWidth: 180, flex: 1 }}>
                      <InputLabel
                        id={`member-user-${team.id}`}
                        shrink
                      >{`Member user for ${team.name}`}</InputLabel>
                      <Select
                        labelId={`member-user-${team.id}`}
                        label={`Member user for ${team.name}`}
                        displayEmpty
                        value={memberId[team.id] || ""}
                        onChange={(e) =>
                          setMemberId((current) => ({
                            ...current,
                            [team.id]: e.target.value,
                          }))
                        }
                      >
                        <MenuItem value="">Add user…</MenuItem>
                        {users.map((u) => (
                          <MenuItem key={u.id} value={String(u.id)}>
                            {u.username}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 190, flex: 1 }}>
                      <InputLabel
                        id={`new-member-role-${team.id}`}
                        shrink
                      >{`New member role for ${team.name}`}</InputLabel>
                      <Select
                        labelId={`new-member-role-${team.id}`}
                        label={`New member role for ${team.name}`}
                        value={roles[team.id] || "MEMBER"}
                        onChange={(e) =>
                          setRoles((current) => ({
                            ...current,
                            [team.id]: e.target.value as Member["role"],
                          }))
                        }
                      >
                        <MenuItem value="ADMIN">ADMIN</MenuItem>
                        <MenuItem value="READONLY">READONLY</MenuItem>
                        <MenuItem value="MEMBER">MEMBER</MenuItem>
                      </Select>
                    </FormControl>
                    <Button
                      variant="contained"
                      color="secondary"
                      size="small"
                      disabled={!memberId[team.id]}
                      onClick={() => void addMember(team.id)}
                    >
                      Add
                    </Button>
                  </Stack>
                </Stack>
              )}
            </Paper>
          ))}
        </Stack>
      </Card>
      {editingUser && (
        <Modal title="Edit user" onClose={() => setEditingUser(null)}>
          <Box
            component="form"
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                const next = await api.updateUser(editingUser.id, {
                  username: editingUser.username,
                  email: editingUser.email || undefined,
                });
                onUsers(users.map((x) => (x.id === next.id ? next : x)));
                setEditingUser(null);
              } catch (err) {
                setEditingUser((cur) =>
                  cur
                    ? {
                        ...cur,
                        error:
                          err instanceof Error
                            ? err.message
                            : "User could not be edited.",
                      }
                    : null,
                );
              }
            }}
          >
            <Stack sx={{ gap: 2, mt: 1 }}>
              <TextField
                label="Username"
                required
                autoFocus
                value={editingUser.username}
                onChange={(e) =>
                  setEditingUser((cur) =>
                    cur ? { ...cur, username: e.target.value } : null,
                  )
                }
              />
              <TextField
                label="Email"
                type="email"
                value={editingUser.email}
                onChange={(e) =>
                  setEditingUser((cur) =>
                    cur ? { ...cur, email: e.target.value } : null,
                  )
                }
              />
              {editingUser.error && (
                <Alert severity="error" role="alert">
                  {editingUser.error}
                </Alert>
              )}
            </Stack>
            <DialogActions sx={{ px: 0, mt: 3 }}>
              <Button
                type="button"
                variant="outlined"
                onClick={() => setEditingUser(null)}
              >
                Cancel
              </Button>
              <Button type="submit" variant="contained">
                Save changes
              </Button>
            </DialogActions>
          </Box>
        </Modal>
      )}
      {membershipsModal && (
        <Modal
          title={`Memberships for ${membershipsModal.user.username}`}
          onClose={() => setMembershipsModal(null)}
        >
          {membershipsModal.error ? (
            <Alert severity="error" role="alert">
              {membershipsModal.error}
            </Alert>
          ) : membershipsModal.loading ? (
            <Typography color="text.secondary">Loading memberships…</Typography>
          ) : membershipsModal.items.length === 0 ? (
            <Typography color="text.secondary">No memberships</Typography>
          ) : (
            <Stack sx={{ gap: 1 }}>
              {membershipsModal.items.map((x) => (
                <Paper
                  key={x.team.id}
                  variant="outlined"
                  sx={{
                    p: 1.5,
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                  }}
                >
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {x.team.name}
                  </Typography>
                  <Chip size="small" label={x.role} />
                </Paper>
              ))}
            </Stack>
          )}
          <DialogActions sx={{ px: 0, mt: 3 }}>
            <Button
              type="button"
              variant="contained"
              onClick={() => setMembershipsModal(null)}
            >
              Close
            </Button>
          </DialogActions>
        </Modal>
      )}
      {editingTeam && (
        <Modal title="Edit team" onClose={() => setEditingTeam(null)}>
          <Box
            component="form"
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                const next = await api.updateTeam(editingTeam.id, {
                  name: editingTeam.name,
                  description: editingTeam.description || undefined,
                });
                onTeams(teams.map((x) => (x.id === next.id ? next : x)));
                setEditingTeam(null);
              } catch (err) {
                setEditingTeam((cur) =>
                  cur
                    ? {
                        ...cur,
                        error:
                          err instanceof Error
                            ? err.message
                            : "Team could not be edited.",
                      }
                    : null,
                );
              }
            }}
          >
            <Stack sx={{ gap: 2, mt: 1 }}>
              <TextField
                label="Team name"
                required
                autoFocus
                value={editingTeam.name}
                onChange={(e) =>
                  setEditingTeam((cur) =>
                    cur ? { ...cur, name: e.target.value } : null,
                  )
                }
              />
              <TextField
                label="Description"
                value={editingTeam.description}
                onChange={(e) =>
                  setEditingTeam((cur) =>
                    cur ? { ...cur, description: e.target.value } : null,
                  )
                }
              />
              {editingTeam.error && (
                <Alert severity="error" role="alert">
                  {editingTeam.error}
                </Alert>
              )}
            </Stack>
            <DialogActions sx={{ px: 0, mt: 3 }}>
              <Button
                type="button"
                variant="outlined"
                onClick={() => setEditingTeam(null)}
              >
                Cancel
              </Button>
              <Button type="submit" variant="contained">
                Save changes
              </Button>
            </DialogActions>
          </Box>
        </Modal>
      )}
    </Box>
  );
}
