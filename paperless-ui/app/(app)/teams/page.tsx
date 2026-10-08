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
import { useI18n } from "@/lib/i18n/use-i18n";
import { useAuth } from "@/components/providers/auth-provider";
import { Badge } from "@/components/ui/card";

export default function TeamsPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const { memberships, data, reload } = useMemberships();
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("teams.title")}
        description={t("teams.subtitle")}
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus />
            {t("actions.create_team")}
          </Button>
        }
      />

      {data === undefined ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-32" />
          ))}
        </div>
      ) : memberships.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Users className="size-6" />}
            title={t("teams.empty_title")}
            description={t("teams.empty_text")}
            action={
              <Button onClick={() => setCreateOpen(true)}>
                <Plus />
                {t("actions.create_team")}
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
                  {team.description || t("teams.no_description")}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <RoleBadge role={role} />
                  {team.owner_id === user?.id && (
                    <Badge variant="warning">{t("teams.owner_badge")}</Badge>
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
