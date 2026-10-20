This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Language

The frontend is English-only. Labels, validation messages, notifications, and date
formatting use English regardless of browser language or previously saved preferences.

## Backend API

The browser calls same-origin `/api/v1/...` URLs. The Next.js rewrite forwards
those requests to `${BACKEND_URL}/api/v1/...`, retaining the version prefix for
JSON requests, multipart uploads, and downloads.

`BACKEND_URL` is the backend origin, without `/api/v1`. For local development it
defaults to `http://localhost:8080`; override it when starting the development server:

```bash
BACKEND_URL=http://localhost:8080 pnpm dev
```

Docker Compose passes `http://backend:8080` as the default build argument.
Production rewrites are generated during the Next.js build, so rebuild the UI image
after changing this value. `NEXT_PUBLIC_API_URL` is not used by the API client.

## Tests

Run `pnpm test` for component regression tests using Vitest and React Testing
Library. Run `pnpm lint` and `pnpm build` for lint and production build checks.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
