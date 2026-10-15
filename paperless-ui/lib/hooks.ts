"use client";

import { useMemo } from "react";
import { useAuth } from "@/components/providers/auth-provider";
import { usersApi } from "@/lib/api/services";
import { useQuery } from "@/lib/use-query";
import type { Role, Team } from "@/lib/types";

/** The authenticated user's teams and roles. */
export function useMemberships() {
  const { user } = useAuth();
  const query = useQuery(user ? `memberships:${user.id}` : null, () =>
    usersApi.teams(user!.id, { silent: true }),
  );

  const memberships = useMemo(() => query.data ?? [], [query.data]);
  const roleByTeam = useMemo(() => {
    const map = new Map<string, Role>();
    for (const membership of memberships) map.set(membership.team.id, membership.role);
    return map;
  }, [memberships]);
  const teamById = useMemo(() => {
    const map = new Map<string, Team>();
    for (const membership of memberships) map.set(membership.team.id, membership.team);
    return map;
  }, [memberships]);
  const writableTeams = useMemo(
    () =>
      memberships
        .filter((m) => m.role === "ADMIN" || m.role === "READ_WRITE")
        .map((m) => m.team),
    [memberships],
  );

  return { ...query, memberships, roleByTeam, teamById, writableTeams };
}
