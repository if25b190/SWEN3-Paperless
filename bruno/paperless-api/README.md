# Paperless API Bruno collection

Import this directory in Bruno and select the `local` environment. Set
`username` and `password` to an existing API user before running the requests.

Run the numbered folders in order. The login response stores `accessToken` and
`userId`; create requests store resource IDs for later requests, and the final
delete requests clean up the resources created by the collection.

The upload request uses `fixtures/sample.pdf`. The collection covers every
operation in `paperless-backend/src/main/resources/openapi.yaml`.
