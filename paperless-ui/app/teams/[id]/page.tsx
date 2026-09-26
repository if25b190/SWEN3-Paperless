"use client";

import React, { useState, useEffect, useCallback, use } from "react";
import { useRouter } from "next/navigation";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import { useAuth } from "@/lib/auth/context";
import { teamsApi, usersApi, ApiError } from "@/lib/api/client";
import {
  TeamResponse,
  TeamMemberResponse,
  UserResponse,
  Role,
} from "@/lib/types/api";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import {
  ArrowLeft,
  UserPlus,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Crown,
  Edit,
  Trash2,
  MoreVertical,
  Loader2,
  Search,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { toast } from "sonner";

export default function TeamDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const resolvedParams = use(params);
  const teamId = resolvedParams.id;

  const { t } = useTranslation();
  const { user } = useAuth();
  const router = useRouter();

  const [team, setTeam] = useState<TeamResponse | null>(null);
  const [members, setMembers] = useState<TeamMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);

  // All registered users for the member picker
  const [allUsers, setAllUsers] = useState<UserResponse[]>([]);

  // Dialog states
  const [editTeamOpen, setEditTeamOpen] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDesc, setEditDesc] = useState("");
  const [editingTeamLoading, setEditingTeamLoading] = useState(false);

  const [deleteTeamOpen, setDeleteTeamOpen] = useState(false);
  const [deletingTeamLoading, setDeletingTeamLoading] = useState(false);

  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<string>("");
  const [selectedRole, setSelectedRole] = useState<Role>("READ_WRITE");
  const [userSearchQuery, setUserSearchQuery] = useState("");
  const [addingMemberLoading, setAddingMemberLoading] = useState(false);
  const [addMemberError, setAddMemberError] = useState<string | null>(null);

  const [changeRoleMember, setChangeRoleMember] = useState<TeamMemberResponse | null>(null);
  const [newRole, setNewRole] = useState<Role>("READONLY");
  const [changingRoleLoading, setChangingRoleLoading] = useState(false);

  const [removeMemberTarget, setRemoveMemberTarget] = useState<TeamMemberResponse | null>(null);
  const [removingMemberLoading, setRemovingMemberLoading] = useState(false);

  const fetchTeamData = useCallback(async () => {
    setLoading(true);
    try {
      const [teamRes, membersRes] = await Promise.all([
        teamsApi.getTeamById(teamId),
        teamsApi.getMembers(teamId),
      ]);
      setTeam(teamRes);
      setMembers(membersRes.items || []);
      setEditName(teamRes.name);
      setEditDesc(teamRes.description || "");
    } catch {
      // Handled by api client
    } finally {
      setLoading(false);
    }
  }, [teamId]);

  useEffect(() => {
    fetchTeamData();
  }, [fetchTeamData]);

  // Load all users when Add Member dialog opens
  useEffect(() => {
    if (addMemberOpen) {
      usersApi
        .getUsers()
        .then((res) => setAllUsers(res.items || []))
        .catch(() => {});
      setSelectedUserId("");
      setSelectedRole("READ_WRITE");
      setUserSearchQuery("");
      setAddMemberError(null);
    }
  }, [addMemberOpen]);

  // Find caller's own role in this team
  const callerMember = members.find((m) => m.user.id === user?.id);
  const isCallerAdmin = callerMember?.role === "ADMIN";
  const isCallerOwner = team?.owner_id === user?.id;

  const handleUpdateTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!team) return;
    setEditingTeamLoading(true);
    try {
      const updated = await teamsApi.updateTeam(team.id, {
        name: editName.trim(),
        description: editDesc.trim() || null,
      });
      setTeam(updated);
      toast.success(t.teams.editSuccess);
      setEditTeamOpen(false);
    } catch {
      // Handled by api client
    } finally {
      setEditingTeamLoading(false);
    }
  };

  const handleDeleteTeam = async () => {
    if (!team) return;
    setDeletingTeamLoading(true);
    try {
      await teamsApi.deleteTeam(team.id);
      toast.success(t.teams.deleteSuccess);
      router.push("/teams");
    } catch {
      // Handled by api client
    } finally {
      setDeletingTeamLoading(false);
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    setAddMemberError(null);

    if (!selectedUserId) {
      setAddMemberError("Please select a user to add.");
      return;
    }

    setAddingMemberLoading(true);
    try {
      await teamsApi.addMember(teamId, {
        user_id: selectedUserId,
        role: selectedRole,
      });
      toast.success(t.teams.addMemberSuccess);
      setAddMemberOpen(false);
      fetchTeamData();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setAddMemberError(err.message);
      } else {
        setAddMemberError("Failed to add member.");
      }
    } finally {
      setAddingMemberLoading(false);
    }
  };

  const handleChangeRole = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!changeRoleMember) return;
    setChangingRoleLoading(true);
    try {
      await teamsApi.updateMemberRole(teamId, changeRoleMember.user.id, {
        role: newRole,
      });
      toast.success(t.teams.changeRoleSuccess);
      setChangeRoleMember(null);
      fetchTeamData();
    } catch {
      // Handled by client
    } finally {
      setChangingRoleLoading(false);
    }
  };

  const handleRemoveMember = async () => {
    if (!removeMemberTarget) return;
    setRemovingMemberLoading(true);
    try {
      await teamsApi.removeMember(teamId, removeMemberTarget.user.id);
      toast.success(t.teams.removeMemberSuccess);
      setRemoveMemberTarget(null);
      fetchTeamData();
    } catch {
      // Handled by client
    } finally {
      setRemovingMemberLoading(false);
    }
  };

  const getRoleBadge = (role: Role) => {
    switch (role) {
      case "ADMIN":
        return (
          <Badge variant="purple" className="gap-1 font-semibold text-xs">
            <ShieldAlert className="h-3 w-3" />
            {t.roles.ADMIN}
          </Badge>
        );
      case "READ_WRITE":
        return (
          <Badge variant="info" className="gap-1 font-medium text-xs">
            <ShieldCheck className="h-3 w-3" />
            {t.roles.READ_WRITE}
          </Badge>
        );
      case "READONLY":
        return (
          <Badge variant="secondary" className="gap-1 font-medium text-xs">
            <Shield className="h-3 w-3 text-slate-500" />
            {t.roles.READONLY}
          </Badge>
        );
    }
  };

  const formatDate = (isoString?: string) => {
    if (!isoString) return "-";
    try {
      return new Date(isoString).toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch {
      return isoString;
    }
  };

  // Filter available users: only those not already members
  const existingMemberUserIds = new Set(members.map((m) => m.user.id));
  const availableUsers = allUsers
    .filter((u) => !existingMemberUserIds.has(u.id))
    .filter((u) =>
      u.username.toLowerCase().includes(userSearchQuery.toLowerCase().trim())
    );

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        {/* Header and Back Link */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button
              variant="outline"
              size="icon"
              onClick={() => router.push("/teams")}
              className="h-9 w-9 border-slate-200 text-slate-600 hover:text-slate-900"
            >
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">
                  {team?.name || t.common.loading}
                </h1>
                {isCallerOwner && (
                  <Badge variant="warning" className="gap-1 text-xs">
                    <Crown className="h-3 w-3" />
                    {t.teams.ownerBadge}
                  </Badge>
                )}
              </div>
              {team?.description && (
                <p className="text-sm text-slate-500 mt-0.5">
                  {team.description}
                </p>
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-2">
            {isCallerAdmin && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setEditTeamOpen(true)}
                  className="gap-1.5"
                >
                  <Edit className="h-4 w-4" />
                  <span>{t.teams.editTeam}</span>
                </Button>
                <Button
                  onClick={() => setAddMemberOpen(true)}
                  className="bg-blue-600 hover:bg-blue-700 text-white gap-1.5 text-sm font-medium"
                >
                  <UserPlus className="h-4 w-4" />
                  <span>{t.teams.addMember}</span>
                </Button>
              </>
            )}

            {isCallerOwner && (
              <Button
                variant="destructive"
                size="sm"
                onClick={() => setDeleteTeamOpen(true)}
                className="gap-1.5 bg-red-600 hover:bg-red-700 text-white"
              >
                <Trash2 className="h-4 w-4" />
                <span>{t.teams.deleteTeam}</span>
              </Button>
            )}
          </div>
        </div>

        {/* Members Section */}
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-2" />
            <p className="text-sm text-slate-500">{t.common.loading}</p>
          </div>
        ) : (
          <Card className="border-slate-200 bg-white shadow-xs overflow-hidden">
            <CardHeader className="py-4 px-6 border-b border-slate-100 flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-base font-semibold">
                  {t.teams.members}
                </CardTitle>
                <p className="text-xs text-slate-500 mt-0.5">
                  {members.length} {t.teams.memberCount}
                </p>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    <tr>
                      <th className="py-3.5 px-6">{t.auth.username}</th>
                      <th className="py-3.5 px-6">{t.teams.role}</th>
                      <th className="py-3.5 px-6">{t.teams.joinedAt}</th>
                      {isCallerAdmin && (
                        <th className="py-3.5 px-6 text-right">
                          {t.common.actions}
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {members.map((member) => {
                      const isOwnerMember = member.user.id === team?.owner_id;
                      const isSelf = member.user.id === user?.id;

                      return (
                        <tr
                          key={member.user.id}
                          className="hover:bg-slate-50/75 transition-colors"
                        >
                          <td className="py-3.5 px-6">
                            <div className="flex items-center gap-3">
                              <Avatar className="h-8 w-8 border border-slate-200">
                                <AvatarFallback className="text-xs font-semibold bg-blue-100 text-blue-700">
                                  {member.user.username.slice(0, 2).toUpperCase()}
                                </AvatarFallback>
                              </Avatar>
                              <div>
                                <p className="font-semibold text-slate-900 flex items-center gap-1.5">
                                  <span>{member.user.username}</span>
                                  {isSelf && (
                                    <span className="text-xs text-slate-400 font-normal">
                                      (you)
                                    </span>
                                  )}
                                  {isOwnerMember && (
                                    <Crown className="h-3.5 w-3.5 text-amber-500" />
                                  )}
                                </p>
                              </div>
                            </div>
                          </td>
                          <td className="py-3.5 px-6">
                            {getRoleBadge(member.role)}
                          </td>
                          <td className="py-3.5 px-6 text-xs text-slate-500">
                            {formatDate(member.joined_at)}
                          </td>
                          {isCallerAdmin && (
                            <td className="py-3.5 px-6 text-right">
                              {isOwnerMember ? (
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <span className="inline-block">
                                      <Button
                                        variant="ghost"
                                        size="icon"
                                        disabled
                                        className="h-8 w-8 opacity-40 cursor-not-allowed"
                                      >
                                        <MoreVertical className="h-4 w-4" />
                                      </Button>
                                    </span>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    {t.teams.ownerProtected}
                                  </TooltipContent>
                                </Tooltip>
                              ) : (
                                <DropdownMenu>
                                  <DropdownMenuTrigger asChild>
                                    <Button
                                      variant="ghost"
                                      size="icon"
                                      className="h-8 w-8 text-slate-500 hover:text-slate-900 cursor-pointer"
                                    >
                                      <MoreVertical className="h-4 w-4" />
                                    </Button>
                                  </DropdownMenuTrigger>
                                  <DropdownMenuContent align="end" className="w-44">
                                    <DropdownMenuItem
                                      onClick={() => {
                                        setChangeRoleMember(member);
                                        setNewRole(member.role);
                                      }}
                                      className="gap-2 cursor-pointer"
                                    >
                                      <Shield className="h-4 w-4 text-slate-500" />
                                      <span>{t.teams.changeRole}</span>
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator />
                                    <DropdownMenuItem
                                      onClick={() => setRemoveMemberTarget(member)}
                                      className="gap-2 text-red-600 focus:text-red-700 focus:bg-red-50 cursor-pointer"
                                    >
                                      <Trash2 className="h-4 w-4" />
                                      <span>{t.teams.removeMember}</span>
                                    </DropdownMenuItem>
                                  </DropdownMenuContent>
                                </DropdownMenu>
                              )}
                            </td>
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Edit Team Dialog */}
        <Dialog open={editTeamOpen} onOpenChange={setEditTeamOpen}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t.teams.editTeam}</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleUpdateTeam} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="edit-team-name">{t.teams.teamName} *</Label>
                <Input
                  id="edit-team-name"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  required
                  maxLength={100}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="edit-team-desc">
                  {t.teams.teamDescription}
                </Label>
                <Textarea
                  id="edit-team-desc"
                  value={editDesc}
                  onChange={(e) => setEditDesc(e.target.value)}
                  maxLength={255}
                />
              </div>
              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setEditTeamOpen(false)}
                  disabled={editingTeamLoading}
                >
                  {t.common.cancel}
                </Button>
                <Button
                  type="submit"
                  disabled={editingTeamLoading || !editName.trim()}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {editingTeamLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.common.save
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>

        {/* Delete Team Dialog */}
        <ConfirmDialog
          open={deleteTeamOpen}
          onOpenChange={setDeleteTeamOpen}
          title={t.teams.deleteTeamConfirmTitle}
          description={t.teams.deleteTeamConfirmText}
          confirmLabel={t.teams.deleteTeam}
          loading={deletingTeamLoading}
          onConfirm={handleDeleteTeam}
        />

        {/* Add Member Dialog */}
        <Dialog open={addMemberOpen} onOpenChange={setAddMemberOpen}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t.teams.addMemberTitle}</DialogTitle>
              <DialogDescription>
                Search registered users and assign an access role.
              </DialogDescription>
            </DialogHeader>

            {addMemberError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {addMemberError}
              </div>
            )}

            <form onSubmit={handleAddMember} className="space-y-4">
              {/* Searchable User Selector */}
              <div className="space-y-1.5">
                <Label>{t.teams.selectUser} *</Label>
                <div className="relative mb-2">
                  <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
                  <Input
                    placeholder={t.teams.userSearchPlaceholder}
                    value={userSearchQuery}
                    onChange={(e) => setUserSearchQuery(e.target.value)}
                    className="pl-8 h-8 text-xs"
                  />
                </div>
                <div className="max-h-48 overflow-y-auto border border-slate-200 rounded-md divide-y divide-slate-100 p-1">
                  {availableUsers.length === 0 ? (
                    <p className="p-3 text-center text-xs text-slate-400">
                      No eligible users found.
                    </p>
                  ) : (
                    availableUsers.map((u) => {
                      const isSelected = selectedUserId === u.id;
                      return (
                        <div
                          key={u.id}
                          onClick={() => setSelectedUserId(u.id)}
                          className={`flex items-center justify-between p-2 rounded cursor-pointer transition-colors text-xs ${
                            isSelected
                              ? "bg-blue-50 text-blue-900 font-semibold"
                              : "hover:bg-slate-50 text-slate-700"
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <Avatar className="h-6 w-6">
                              <AvatarFallback className="text-[10px]">
                                {u.username.slice(0, 2).toUpperCase()}
                              </AvatarFallback>
                            </Avatar>
                            <span>{u.username}</span>
                          </div>
                          {isSelected && (
                            <Badge variant="default" className="text-[10px] h-5">
                              Selected
                            </Badge>
                          )}
                        </div>
                      );
                    })
                  )}
                </div>
              </div>

              {/* Role Selector */}
              <div className="space-y-1.5">
                <Label>{t.teams.role} *</Label>
                <Select
                  value={selectedRole}
                  onValueChange={(val) => setSelectedRole(val as Role)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t.teams.selectRole} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="READONLY">{t.roles.READONLY}</SelectItem>
                    <SelectItem value="READ_WRITE">{t.roles.READ_WRITE}</SelectItem>
                    <SelectItem value="ADMIN">{t.roles.ADMIN}</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setAddMemberOpen(false)}
                  disabled={addingMemberLoading}
                >
                  {t.common.cancel}
                </Button>
                <Button
                  type="submit"
                  disabled={addingMemberLoading || !selectedUserId}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {addingMemberLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.teams.addMember
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>

        {/* Change Role Dialog */}
        <Dialog
          open={!!changeRoleMember}
          onOpenChange={(open) => !open && setChangeRoleMember(null)}
        >
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t.teams.changeRoleTitle}</DialogTitle>
              <DialogDescription>
                Update the permissions for {changeRoleMember?.user.username}.
              </DialogDescription>
            </DialogHeader>

            <form onSubmit={handleChangeRole} className="space-y-4">
              <div className="space-y-1.5">
                <Label>{t.teams.role} *</Label>
                <Select
                  value={newRole}
                  onValueChange={(val) => setNewRole(val as Role)}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="READONLY">{t.roles.READONLY}</SelectItem>
                    <SelectItem value="READ_WRITE">{t.roles.READ_WRITE}</SelectItem>
                    <SelectItem value="ADMIN">{t.roles.ADMIN}</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setChangeRoleMember(null)}
                  disabled={changingRoleLoading}
                >
                  {t.common.cancel}
                </Button>
                <Button
                  type="submit"
                  disabled={changingRoleLoading}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {changingRoleLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.common.save
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>

        {/* Remove Member Confirmation Dialog */}
        <ConfirmDialog
          open={!!removeMemberTarget}
          onOpenChange={(open) => !open && setRemoveMemberTarget(null)}
          title={t.teams.removeMemberConfirmTitle}
          description={t.teams.removeMemberWarning}
          confirmLabel={t.teams.removeMember}
          loading={removingMemberLoading}
          onConfirm={handleRemoveMember}
        />
      </div>
    </ProtectedRoute>
  );
}
