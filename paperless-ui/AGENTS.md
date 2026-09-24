<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

## UI structure

Keep the route thin and put feature behavior in focused components. `app/page.tsx` renders
`Workspace`; the existing `app/layout.tsx`, `app/providers.tsx`, `app/globals.css`, and
`lib/api.ts` remain the app shell, providers, global styles, and API client respectively.

```text
components/
  workspace/
    Workspace.tsx             # Compose the workspace and coordinate its active view
    WorkspaceNavigation.tsx   # Render navigation and report the selected view
    types.ts                   # Shared workspace types
  documents/
    Filters.tsx                # Document search and filter controls
    Pagination.tsx             # Document result paging controls
    DocumentGrid.tsx           # Render document results and selection
    DocumentDetail.tsx         # Render the selected document and its actions
    UploadDialog.tsx           # Collect and submit document uploads
    document-utils.ts          # Pure document helpers
  auth/
    LoginDialog.tsx            # Sign-in dialog
  people/
    People.tsx                 # People management view
  settings/
    Settings.tsx               # Settings view
  shared/
    Modal.tsx                  # Reusable modal primitive
    styles.ts                  # Shared style definitions
```
