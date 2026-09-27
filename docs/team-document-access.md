# Team and Document Access

Registration (`POST /users`) is public, but accounts do not share documents by default. An unassigned document is private to its uploader. A document assigned to a team is visible to that team's current members; removing a member also removes their access, even if they uploaded the document.

| Team role | Document permissions |
| --- | --- |
| `READONLY` | Read, search, and download team documents |
| `READ_WRITE` | `READONLY` permissions, plus upload, edit, and delete team documents |
| `ADMIN` | `READ_WRITE` permissions, plus team membership management |

The team owner is immutable: the owner must remain an `ADMIN` and cannot be removed. Only the document uploader can change a document's sharing. Moving a team document requires write permission on its source team; assigning it to a destination team also requires write permission there. Clearing team sharing returns the document to uploader-only access.

Team deletion is owner-only and may conflict with documents still assigned to that team. Account deletion is blocked when the account owns a team or is its last `ADMIN`; remaining document references can also conflict with deletion. Resolve dependent records deliberately before retrying.

## Existing data and local storage

Persisted team and document records from before owner tracking lack valid owner assignments, and stored `MEMBER` role values do not match the current role names. Existing data requires a deliberate migration; a fresh database is an alternative only for an intentionally disposable environment. No data reset or migration was performed as part of this documentation update.

Uploaded files are stored on the backend's local temporary filesystem, not in PostgreSQL. They are not durable across backend container replacement. PostgreSQL concurrency tests have not been executed.
