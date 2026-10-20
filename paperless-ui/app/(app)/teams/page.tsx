"use client";

import { useState } from "react";
import Link from "next/link";
import { ChevronRight, Plus, Users } from "lucide-react";
import { RoleBadge } from "@/components/badges";
import { EmptyState, PageHeader } from "@/components/common";
import { TeamDialog } from "@/components/teams/team-dialog";
import { Button } from "@/components/ui/button";
import { Card, Skeleton } from "@/components/ui/card";
import { useMemberships } from "@/lib/hooks";
import { useAuth } from "@/components/providers/auth-provider";
import { Badge } from "@/components/ui/card";

export default function TeamsPage() {
  const { user } = useAuth();
  const { memberships, data, loading, error, reload } = useMemberships();
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="grid gap-6">
      <PageHeader
        title="Teams"
        description="Share documents with colleagues and manage access."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus />
            Create Team
          </Button>
        }
      />

      {data === undefined && loading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-32" />
          ))}
        </div>
      ) : error ? (
        <Card>
          <EmptyState
            icon={<Users className="size-6" />}
            title="Teams could not be loaded."
            description="Please try again."
            action={<Button variant="outline" onClick={reload}>Retry</Button>}
          />
        </Card>
      ) : memberships.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Users className="size-6" />}
            title="You are not in any team yet"
            description="Create a team to share documents with others."
            action={
              <Button onClick={() => setCreateOpen(true)}>
                <Plus />
                Create Team
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {memberships.map(({ team, role }) => (
            <Link key={team.id} href={`/teams/${team.id}`} className="group">
              <Card className="h-full p-5 transition-shadow group-hover:shadow-md">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Users className="size-5" />
                  </div>
                  <ChevronRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                </div>
                <h2 className="mt-3 truncate font-semibold">{team.name}</h2>
                <p className="mt-1 line-clamp-2 min-h-10 text-sm text-muted-foreground">
                  {team.description || "No description."}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <RoleBadge role={role} />
                  {team.owner_id === user?.id && (
                    <Badge variant="warning">Owner</Badge>
                  )}
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <TeamDialog open={createOpen} team={null} onOpenChange={setCreateOpen} onSaved={() => reload()} />
    </div>
  );
}
