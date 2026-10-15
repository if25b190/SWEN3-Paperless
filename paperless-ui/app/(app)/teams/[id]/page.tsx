"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Pencil, Trash2, Users } from "lucide-react";
import { toast } from "sonner";
import { RoleBadge } from "@/components/badges";
import { ConfirmDialog, EmptyState, PageHeader } from "@/components/common";
import { useAuth } from "@/components/providers/auth-provider";
import { TeamDialog } from "@/components/teams/team-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, Skeleton } from "@/components/ui/card";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { teamsApi, usersApi } from "@/lib/api/services";
import { useQuery } from "@/lib/use-query";
import { formatDay } from "@/lib/utils";

export default function TeamDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { user } = useAuth();

  const team = useQuery(`team:${id}`, () => teamsApi.get(id));
  const members = useQuery(`team-members:${id}`, () => teamsApi.members(id));
  const users = useQuery(`users:${id}`, () => usersApi.list({ silent: true }));

  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  if (team.error && !team.data) {
    return (
      <Card>
        <EmptyState
          icon={<Users className="size-6" />}
          title="This team could not be loaded."
          action={
            <Button asChild variant="outline">
              <Link href="/teams">
                <ArrowLeft />
                Back
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
    "Unknown";

  async function confirmDeleteTeam() {
    setDeleteBusy(true);
    try {
      await teamsApi.remove(id);
      toast.success("Team deleted.");
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
            Teams
          </Link>
        </Button>
        <PageHeader
          title={current.name}
          description={current.description || "No description."}
          actions={
            <>
              {isAdmin && (
                <Button variant="outline" onClick={() => setEditOpen(true)}>
                  <Pencil />
                  Edit Team
                </Button>
              )}
              {isOwner ? (
                <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                  <Trash2 />
                  Delete Team
                </Button>
              ) : (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button variant="destructive" disabled>
                        <Trash2 />
                        Delete Team
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>Only the team owner can delete the team.</TooltipContent>
                </Tooltip>
              )}
            </>
          }
        />
      </div>

      <div className="grid items-start gap-6 lg:max-w-lg">
        <Card>
          <CardHeader>
            <CardTitle>Team details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              <div className="grid gap-0.5 py-2.5">
                <dt className="text-xs font-medium text-muted-foreground">Owner</dt>
                <dd className="text-sm">{ownerName}</dd>
              </div>
              <div className="grid gap-1 py-2.5">
                <dt className="text-xs font-medium text-muted-foreground">Your role</dt>
                <dd>{myRole ? <RoleBadge role={myRole} /> : "-"}</dd>
              </div>
              <div className="grid gap-0.5 py-2.5">
                <dt className="text-xs font-medium text-muted-foreground">Created</dt>
                <dd className="text-sm">{formatDay(current.created_at)}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

      </div>

      <TeamDialog open={editOpen} team={current} onOpenChange={setEditOpen} onSaved={() => team.reload()} />
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete team?"
        description={`"${current.name}" and all its memberships will be deleted. Shared documents stay with their uploaders.`}
        confirmLabel="Delete Team"
        loading={deleteBusy}
        onConfirm={confirmDeleteTeam}
      />
    </div>
  );
}
