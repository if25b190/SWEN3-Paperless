"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import { useAuth } from "@/lib/auth/context";
import { teamsApi, usersApi, ApiError } from "@/lib/api/client";
import { Role, UserTeamMembershipResponse } from "@/lib/types/api";
import {
  Users,
  Plus,
  ArrowRight,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Loader2,
  Crown,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "sonner";

export default function TeamsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const router = useRouter();

  const [memberships, setMemberships] = useState<UserTeamMembershipResponse[]>([]);
  const [loading, setLoading] = useState(true);

  // Create Team Dialog state
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [teamName, setTeamName] = useState("");
  const [teamDescription, setTeamDescription] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const fetchTeams = useCallback(async () => {
    if (!user?.id) return;
    setLoading(true);
    try {
      const res = await usersApi.getUserTeams(user.id);
      setMemberships(res.items || []);
    } catch {
      // Handled by api client
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    fetchTeams();
  }, [fetchTeams]);

  const handleCreateTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);

    if (!teamName.trim()) {
      setCreateError("Team name cannot be empty.");
      return;
    }

    setCreating(true);
    try {
      const newTeam = await teamsApi.createTeam({
        name: teamName.trim(),
        description: teamDescription.trim() || null,
      });
      toast.success(t.teams.createSuccess);
      setCreateDialogOpen(false);
      setTeamName("");
      setTeamDescription("");
      router.push(`/teams/${newTeam.id}`);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setCreateError(err.message);
      } else {
        setCreateError("Failed to create team.");
      }
    } finally {
      setCreating(false);
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

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              {t.teams.title}
            </h1>
            <p className="text-sm text-slate-500">{t.teams.subtitle}</p>
          </div>
          <Button
            onClick={() => setCreateDialogOpen(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white gap-2 font-medium"
          >
            <Plus className="h-4 w-4" />
            {t.teams.createTeam}
          </Button>
        </div>

        {/* Teams List */}
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-2" />
            <p className="text-sm text-slate-500">{t.common.loading}</p>
          </div>
        ) : memberships.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200 text-center">
            <div className="h-12 w-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
              <Users className="h-6 w-6" />
            </div>
            <h3 className="text-base font-semibold text-slate-900">
              {t.teams.noTeamsFound}
            </h3>
            <p className="text-sm text-slate-500 max-w-sm mt-1 mb-4">
              {t.teams.noTeamsSubtitle}
            </p>
            <Button
              onClick={() => setCreateDialogOpen(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white gap-2 font-medium"
            >
              <Plus className="h-4 w-4" />
              {t.teams.createTeam}
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {memberships.map(({ team, role }) => {
              const isOwner = user?.id === team.owner_id;
              return (
                <Card
                  key={team.id}
                  className="border-slate-200 bg-white hover:border-slate-300 transition-shadow hover:shadow-xs flex flex-col justify-between"
                >
                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="space-y-1">
                        <CardTitle className="text-lg font-semibold text-slate-900 flex items-center gap-2">
                          <span>{team.name}</span>
                          {isOwner && (
                            <span
                              title={t.teams.ownerBadge}
                              className="inline-flex text-amber-500"
                            >
                              <Crown className="h-4 w-4" />
                            </span>
                          )}
                        </CardTitle>
                        {team.description ? (
                          <CardDescription className="line-clamp-2">
                            {team.description}
                          </CardDescription>
                        ) : (
                          <p className="text-xs text-slate-400 italic">
                            No description provided
                          </p>
                        )}
                      </div>
                    </div>
                  </CardHeader>

                  <CardContent className="pt-0">
                    <div className="flex items-center justify-between pt-4 border-t border-slate-100 text-xs text-slate-500">
                      <div>{getRoleBadge(role)}</div>
                      <Link
                        href={`/teams/${team.id}`}
                        className="inline-flex items-center gap-1 font-medium text-blue-600 hover:underline"
                      >
                        <span>{t.teams.members}</span>
                        <ArrowRight className="h-3.5 w-3.5" />
                      </Link>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}

        {/* Create Team Dialog */}
        <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t.teams.createTitle}</DialogTitle>
              <DialogDescription>
                Create a team to collaborate and share documents. You will be the team owner.
              </DialogDescription>
            </DialogHeader>

            {createError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {createError}
              </div>
            )}

            <form onSubmit={handleCreateTeam} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="team-name">{t.teams.teamName} *</Label>
                <Input
                  id="team-name"
                  value={teamName}
                  onChange={(e) => setTeamName(e.target.value)}
                  placeholder={t.teams.teamNamePlaceholder}
                  required
                  maxLength={100}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="team-desc">{t.teams.teamDescription}</Label>
                <Textarea
                  id="team-desc"
                  value={teamDescription}
                  onChange={(e) => setTeamDescription(e.target.value)}
                  placeholder={t.teams.teamDescriptionPlaceholder}
                  maxLength={255}
                />
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setCreateDialogOpen(false)}
                  disabled={creating}
                >
                  {t.common.cancel}
                </Button>
                <Button
                  type="submit"
                  disabled={creating || !teamName.trim()}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {creating ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.teams.createTeam
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>
    </ProtectedRoute>
  );
}
