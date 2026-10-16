-- Team deletion cascades to memberships at the database level.
ALTER TABLE team_members DROP CONSTRAINT fk_team_members_team;
ALTER TABLE team_members ADD CONSTRAINT fk_team_members_team
    FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE;
