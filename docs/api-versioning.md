# API versioning

All application endpoints use the `/api/v1` prefix, including registration and login.
For example, register at `POST /api/v1/users`, log in at `POST /api/v1/auth/login`,
and list documents at `GET /api/v1/documents`.

The OpenAPI paths include the version prefix; the server URL remains the server origin.
Generated Spring interfaces therefore define the same paths as the published contract.
Creation responses return versioned `Location` headers.
Swagger UI and the OpenAPI documentation remain at `/swagger-ui.html` and `/v3/api-docs`.

Breaking changes require a new major URL version (for example `/api/v2`). Additive,
backward-compatible changes stay in v1. Unsupported versions and unversioned application
paths have no controller mapping and return 404 to authenticated clients. The bearer
security filter still returns 401 first for unauthenticated requests outside public routes.
