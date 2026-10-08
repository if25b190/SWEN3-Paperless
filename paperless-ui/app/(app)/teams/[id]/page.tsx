"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Lock, MoreHorizontal, Pencil, ShieldAlert, Trash2, UserCog, UserMinus, UserPlus, Users } from "lucide-react";
import { toast } from "sonner";
import { RoleBadge } from "@/components/badges";
import { ConfirmDialog, EmptyState, PageHeader } from "@/components/common";
import { useAuth } from "@/components/providers/auth-provider";
import { AddMemberDialog, ChangeRoleDialog } from "@/components/teams/member-dialogs";
import { TeamDialog } from "@/components/teams/team-dialog";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Badge, Card, CardContent, CardHeader, CardTitle, Skeleton, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { teamsApi, usersApi } from "@/lib/api/services";
import { useI18n } from "@/lib/i18n/use-i18n";
import type { TeamMember } from "@/lib/types";
import { useQuery } from "@/lib/use-query";
import { formatDay } from "@/lib/utils";

export default function TeamDetailPage() {
  const { t, locale } = useI18n();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { user } = useAuth();

  const team = useQuery(`team:${id}`, () => teamsApi.get(id));
  const members = useQuery(`team-members:${id}`, () => teamsApi.members(id));
  const users = useQuery(`users:${id}`, () => usersApi.list({ silent: true }));

  const [addOpen, setAddOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [roleTarget, setRoleTarget] = useState<TeamMember | null>(null);
  const [removeTarget, setRemoveTarget] = useState<TeamMember | null>(null);
  const [removeBusy, setRemoveBusy] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  if (team.error && !team.data) {
    return (
      <Card>
        <EmptyState
          icon={<Users className="size-6" />}
          title={t("teams.detail_not_found")}
          action={
            <Button asChild variant="outline">
              <Link href="/teams">
                <ArrowLeft />
                {t("common.back")}
              </Link>
            </Button>
          }
        />
      </Card>
    );
  }
  if (!team.data) {
    return <Skeleton className="h-64 w-full" />;
  }

  const current = team.data;
  const memberList = members.data ?? [];
  const myRole = memberList.find((member) => member.user.id === user?.id)?.role;
  const isAdmin = myRole === "ADMIN";
  const isOwner = current.owner_id === user?.id;
  const ownerName =
    memberList.find((member) => member.user.id === current.owner_id)?.user.username ??
    users.data?.find((u) => u.id === current.owner_id)?.username ??
    t("common.unknown");

  const memberIds = new Set(memberList.map((member) => member.user.id));
  const candidates = (users.data ?? []).filter((u) => !memberIds.has(u.id));

  async function confirmRemove() {
    if (!removeTarget) return;
    setRemoveBusy(true);
    try {
      await teamsApi.removeMember(id, removeTarget.user.id);
      toast.success(t("messages.member_removed_success"));
      setRemoveTarget(null);
      members.reload();
    } catch {
      // toast already shown by the API client
    } finally {
      setRemoveBusy(false);
    }
  }

  async function confirmDeleteTeam() {
    setDeleteBusy(true);
    try {
      await teamsApi.remove(id);
      toast.success(t("messages.team_deleted"));
      router.replace("/teams");
    } catch {
      setDeleteBusy(false);
    }
  }

  return (
    <div className="grid gap-6">
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-3 mb-1">
          <Link href="/teams">
            <ArrowLeft />
            {t("nav.teams")}
          </Link>
        </Button>
        <PageHeader
          title={current.name}
          description={current.description || t("teams.no_description")}
          actions={
            <>
              {isAdmin && (
                <Button variant="outline" onClick={() => setEditOpen(true)}>
                  <Pencil />
                  {t("actions.edit_team")}
                </Button>
              )}
              {isOwner ? (
                <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                  <Trash2 />
                  {t("actions.delete_team")}
                </Button>
              ) : (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button variant="destructive" disabled>
                        <Trash2 />
                        {t("actions.delete_team")}
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{t("teams.delete_owner_only")}</TooltipContent>
                </Tooltip>
              )}
            </>
          }
        />
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[18rem_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>{t("teams.details")}</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              <div className="grid gap-0.5 py-2.5">
                <dt className="text-xs font-medium text-muted-foreground">{t("teams.owner")}</dt>
                <dd className="text-sm">{ownerName}</dd>
              </div>
              <div className="grid gap-1 py-2.5">
                <dt className="text-xs font-medium text-muted-foreground">{t("teams.your_role")}</dt>
                <dd>{myRole ? <RoleBadge role={myRole} /> : "-"}</dd>
              </div>
              <div className="grid gap-0.5 py-2.5">
                <dt className="text-xs font-medium text-muted-foreground">{t("teams.created")}</dt>
                <dd className="text-sm">{formatDay(current.created_at, locale)}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card className="overflow-hidden p-0">
          <div className="flex flex-wrap items-center justify-between gap-3 p-5 pb-3">
            <div>
              <h2 className="text-base font-semibold">{t("teams.members")}</h2>
              <p className="text-sm text-muted-foreground">
                {t("teams.members_count", { count: memberList.length })}
              </p>
            </div>
            {isAdmin ? (
              <Button onClick={() => setAddOpen(true)}>
                <UserPlus />
                {t("actions.add_member")}
              </Button>
            ) : (
              myRole && (
                <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <ShieldAlert className="size-3.5" />
                  {t("teams.admin_only")}
                </p>
              )
            )}
          </div>
          {members.data === undefined ? (
            <div className="grid gap-2 p-5">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-12" />
              ))}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>{t("teams.col_member")}</TableHead>
                  <TableHead>{t("teams.col_role")}</TableHead>
                  <TableHead>{t("teams.col_joined")}</TableHead>
                  {isAdmin && <TableHead className="text-right">{t("common.actions")}</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {memberList.map((member) => {
                  const memberIsOwner = member.user.id === current.owner_id;
                  return (
                    <TableRow key={member.user.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <Avatar className="size-8">
                            <AvatarFallback>{member.user.username.slice(0, 2)}</AvatarFallback>
                          </Avatar>
                          <span className="font-medium">{member.user.username}</span>
                          {member.user.id === user?.id && (
                            <Badge variant="muted">{t("common.you")}</Badge>
                          )}
                          {memberIsOwner && <Badge variant="warning">{t("teams.owner_badge")}</Badge>}
                        </div>
                      </TableCell>
                      <TableCell>
                        <RoleBadge role={member.role} />
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatDay(member.joined_at, locale)}
                      </TableCell>
                      {isAdmin && (
                        <TableCell className="text-right">
                          {memberIsOwner ? (
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <span tabIndex={0} className="inline-flex">
                                  <Button variant="ghost" size="icon" disabled aria-label={t("messages.owner_protected")}>
                                    <Lock />
                                  </Button>
                                </span>
                              </TooltipTrigger>
                              <TooltipContent>{t("teams.owner_tooltip")}</TooltipContent>
                            </Tooltip>
                          ) : (
                            <DropdownMenu>
                              <DropdownMenuTrigger asChild>
                                <Button variant="ghost" size="icon" aria-label={t("common.more")}>
                                  <MoreHorizontal />
                                </Button>
                              </DropdownMenuTrigger>
                              <DropdownMenuContent align="end">
                                <DropdownMenuItem onSelect={() => setRoleTarget(member)}>
                                  <UserCog />
                                  {t("actions.change_role")}
                                </DropdownMenuItem>
                                <DropdownMenuSeparator />
                                <DropdownMenuItem variant="destructive" onSelect={() => setRemoveTarget(member)}>
                                  <UserMinus />
                                  {t("actions.remove_member")}
                                </DropdownMenuItem>
                              </DropdownMenuContent>
                            </DropdownMenu>
                          )}
                        </TableCell>
                      )}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Card>
      </div>

      <AddMemberDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        teamId={id}
        candidates={candidates}
        onAdded={() => members.reload()}
      />
      <ChangeRoleDialog
        teamId={id}
        member={roleTarget}
        onOpenChange={(open) => !open && setRoleTarget(null)}
        onSaved={() => members.reload()}
      />
      <ConfirmDialog
        open={removeTarget !== null}
        onOpenChange={(open) => !open && setRemoveTarget(null)}
        title={t("teams.remove_title")}
        description={t("teams.remove_text", { name: removeTarget?.user.username ?? "" })}
        confirmLabel={t("actions.remove_member")}
        loading={removeBusy}
        onConfirm={confirmRemove}
      />
      <TeamDialog open={editOpen} team={current} onOpenChange={setEditOpen} onSaved={() => team.reload()} />
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={t("teams.delete_title")}
        description={t("teams.delete_text", { name: current.name })}
        confirmLabel={t("actions.delete_team")}
        loading={deleteBusy}
        onConfirm={confirmDeleteTeam}
      />
    </div>
  );
}
