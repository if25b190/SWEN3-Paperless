import { useState } from "react";
import {
  Box,
  Button,
  Card,
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
                        const username = prompt("Username", detail.username);
                        if (username)
                          return api
                            .updateUser(u.id, { username })
                            .then((next) =>
                              onUsers(
                                users.map((x) => (x.id === next.id ? next : x)),
                              ),
                            );
                        return undefined;
                      })
                      .catch((err) => onError(err, "User could not be edited."))
                  }
                >
                  Edit
                </Button>
                <Button
                  size="small"
                  onClick={() =>
                    api
                      .userTeams(u.id)
                      .then((result) =>
                        alert(
                          result.items
                            .map((x) => `${x.team.name}: ${x.role}`)
                            .join("\n") || "No memberships",
                        ),
                      )
                      .catch((err) =>
                        onError(err, "Memberships could not be loaded."),
                      )
                  }
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
                          const renamed = prompt("Team name", detail.name);
                          if (renamed)
                            return api
                              .updateTeam(team.id, { name: renamed })
                              .then((next) =>
                                onTeams(
                                  teams.map((x) =>
                                    x.id === next.id ? next : x,
                                  ),
                                ),
                              );
                          return undefined;
                        })
                        .catch((err) =>
                          onError(err, "Team could not be edited."),
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
    </Box>
  );
}
