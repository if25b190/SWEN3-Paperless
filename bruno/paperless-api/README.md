# Paperless API Bruno collection

Import this directory in Bruno and select the `local` environment. Set
`username` and `password` to an existing API user before running the collection;
the first login requires a user to already exist. If the database is fresh,
register that first user through the public endpoint, then use those credentials
in `local`:

```sh
curl -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"bruno_user","password":"ChangeMe123!"}'
```

The collection uses fixed account names: `bruno_user` for the initial account
and `bruno_member` for the second account, which is renamed to
`bruno_member_renamed` during the run. If a run is interrupted, a leftover
`bruno_member` will make the next registration return 409. Delete the existing
`bruno_member` from the local database (or reset the local database), then rerun
`02-create-user.bru` successfully before continuing with later requests. If
`bruno_user` already exists, use its credentials instead of registering it
again; bootstrap it only once. Do not add generated suffixes to these usernames.

Run the numbered folders in order. `local.bru` initializes `accessToken`,
`initialAccessToken`, and `memberAccessToken` as empty values. The first login
sets both `accessToken` and `initialAccessToken`; the second login replaces only
`accessToken`, and the old-token request confirms that `initialAccessToken` is
rejected. Public registration creates a second user, whose login populates
`memberAccessToken` without replacing the primary user's token. Requests use
the primary token for team administration and the second user's token for
membership/document access. Runtime resource IDs are UUID strings captured from
responses by the requests and reused as strings in later URLs and JSON bodies;
use the collection runner so they execute in order. The `sample.pdf` fixture is
only an upload payload and contains no API IDs.

`createdUserId` is populated only by a successful public registration response;
`accessToken` (including either login) does not set it. The created-user GET
request stops with an explicit error if no valid `createdUserId` is available,
so rerun the create request successfully rather than substituting the primary
user's `userId`.

Document-type names are generated per run so repeated runs do not collide with
earlier metadata.

Validation checks also expect HTTP 400 for registration with a three-character
username or seven-character password, a whitespace-only team name, and a blank
document title. These rejected requests do not create resources.

The upload requests use `fixtures/sample.pdf` and create private documents for
both users plus a team-shared document uploaded by the second user. The collection
checks both teams' immutable owner IDs, rejected owner demotion/removal, private
and team document-list visibility, and the uploader's private-to-team-to-private
sharing transitions (including metadata-only updates and invalid/forbidden
changes). The second user is promoted to `READ_WRITE` to upload and share, then
changed to `READONLY` to check read access and denied write operations. After the
uploader is removed from the team, the collection checks both direct and list
access revocation. All three documents are deleted before metadata, teams, or the
created account are removed.
