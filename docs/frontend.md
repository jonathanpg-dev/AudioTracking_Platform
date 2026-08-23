# Frontend / user interface (Phase 7)

## Stack and why

React 19 + TypeScript (strict) + Vite, built as a single-page app that talks to the existing
Spring Boot REST API over `fetch` — no server-side rendering, no meta-framework. Chosen because
the backend is already a complete, independent REST API; a thin client is the natural fit, and it
keeps the two halves deployable separately.

| Concern | Choice | Why |
|---|---|---|
| Server state (API data) | TanStack Query | Caching, request de-duplication, and cache invalidation are exactly what most of this app's state *is* — no separate global store needed for it. |
| Client state (forms, auth token) | plain `useState`/React Context | Small enough not to justify Redux/Zustand; see `AuthContext`. |
| Styling | Tailwind CSS v4 | Utility-first, no separate CSS files to keep in sync with components; v4's `@theme` in `src/index.css` replaces `tailwind.config.js`. |
| Accessible primitives | Radix UI (Dialog, Tabs, DropdownMenu) | Focus trapping, ARIA wiring, and keyboard navigation for free — not worth hand-rolling for a portfolio project. |
| Charts | Recharts | Analytics pages (Phase 6 data) need a handful of bar/line charts, not a full visualization library. |
| Tests | Vitest + React Testing Library + MSW | Same conceptual split as the backend's tests: MSW mocks the network boundary the way `@SpringBootTest` mocks nothing and hits a real (test) database — the frontend equivalent of an integration test. |

## Directory layout

```
frontend/src/
  api/          one file per backend resource (assets.ts, projects.ts, ...) + client.ts (fetch
                 wrapper), tokenStorage.ts (localStorage), queryClient.ts (TanStack Query config)
  types/        TypeScript types mirroring backend DTOs exactly, one file per resource
  components/ui/  generic, app-agnostic building blocks (Button, Dialog, EmptyState, ...)
  features/     one folder per domain area (assets, projects, collaboration, ...) — each holds
                 its TanStack Query hooks (hooks.ts) and any feature-specific components
  layouts/      AppLayout (sidebar + mobile drawer) and its pieces
  pages/        one file per route, composing features/ and components/ui/
  utils/        formatting, query-string building, error-message extraction
  test/         Vitest setup, MSW server + handlers, a renderWithProviders test helper
```

## Talking to the backend

`api/client.ts` is the single chokepoint every request goes through — mirrors the backend's own
"one authorization chokepoint" pattern (see `docs/collaboration.md`). It:

- Reads the base URL from `VITE_API_BASE_URL` (never hardcoded).
- Attaches `Authorization: Bearer <token>` from `tokenStorage.ts` when a token is present.
- Throws a typed `ApiRequestError` (status + the backend's own JSON error body) on any non-2xx
  response, so every page/hook works with the same shape of error regardless of which endpoint
  failed — same idea as the backend's `GlobalExceptionHandler` producing one consistent error body.
- Calls a single registered "unauthorized" handler on 401, instead of every call site handling it —
  see the auth flow below.

## Auth flow

1. `LoginPage`/`RegisterPage` call the backend, get a JWT back, and hand it to
   `AuthContext#signIn`, which writes it to `localStorage` (`tokenStorage.ts`) and into React
   state in the same call.
2. `AuthContext` keeps the token in `useState` *in addition to* `localStorage` specifically so
   sign-in/sign-out re-render the app immediately, instead of only taking effect after a reload —
   `localStorage` alone has no change-notification mechanism React can subscribe to.
3. Whenever a token is present, `AuthContext` runs a `GET /api/v1/users/me` query to resolve it to
   a `CurrentUser` (distinct from the plain `User` returned by registration — it carries the two
   live-computed `isClientOnly`/`isLinkedAsClient` flags described in `docs/collaboration.md`, used
   to drive the simplified client UI below). `isAuthenticated` is only true once that query has
   actually succeeded — holding a token that turns out to be expired/invalid is treated as logged
   out, not as a loading state that never resolves.
4. `ProtectedRoute` reads `isAuthenticated`/`isLoading` from `AuthContext` and redirects to
   `/login` when not authenticated. This is a UX convenience only — every real authorization
   decision still happens on the backend (see "Permission-aware UI" below).
5. Any request that comes back 401 — token expired, revoked, whatever — triggers the single
   handler `AuthContext` registered with `api/client.ts` on mount: sign out and redirect to
   `/login`. No individual page has to know this can happen.
6. Google Sign-In (`GoogleSignInButton`) uses Google's own Identity Services script to obtain an
   ID token client-side, then posts it to the existing `POST /api/v1/auth/google` endpoint, which
   returns the same kind of JWT as password login — from here on, the flow is identical.

## Permission-aware UI

The UI reflects permissions the backend already computed — it never re-derives or enforces them.
Concretely: `ProjectResponse.myRole` tells the frontend whether the current user is `OWNER`,
`EDIT`, `VIEW`, or `CLIENT` on a Project (see `docs/collaboration.md`), and components like
`CollaboratorsPanel` show/hide management controls based on that field alone. If a VIEW
collaborator forged a request to an owner-only endpoint, the backend would still reject it with a
403 exactly as it does today — the UI hiding the button is purely to avoid showing controls that
would fail, never a security boundary.

Every `myRole` check on the frontend (`ProjectDetailPage`, `AssetDetailPage`) is written as an
**allow-list** (`myRole === 'OWNER' || myRole === 'EDIT'`), never a deny-list
(`myRole !== 'VIEW'`) — a deny-list silently grants edit rights to any *future* role the moment
it's added to `ProjectRole`, which is exactly what happened when `CLIENT` was introduced: both
pages had a `!== 'VIEW'` check that would otherwise have quietly let a CLIENT edit Project assets.
Caught by an audit before any client-facing UI was built on top, not by a live bug report.

## Client-only accounts get a simplified UI

A `CurrentUser.isClientOnly` account (see `docs/collaboration.md`) owns nothing of its own, so
most of the app has nothing to show it. Two pieces keep this consistent rather than relying on
every page to individually handle "what if this account owns nothing":

- `layouts/navItems.ts#getNavItems` returns a single `Projects` entry (pointing at
  `/client-projects`) for an `isClientOnly` account instead of the full nav, and adds an extra
  `Client Projects` entry to the full nav for a dual-role account (`isLinkedAsClient` but *not*
  `isClientOnly` — owns things of their own too).
- `App.tsx`'s `ClientOnlyGate` backs the hidden nav links up with an actual redirect: an
  `isClientOnly` account hitting any URL outside an allow-list (`/client-projects`, a Project
  detail page, an Asset detail page) is sent to `/client-projects`. Without this, hiding the nav
  link would be purely cosmetic — the Dashboard/Assets/Collections/Clients/Analytics pages would
  still render (mostly empty, since the account owns nothing) for anyone who typed the URL
  directly.

`pages/ClientProjectsPage.tsx` lists `GET /projects/as-client` — a separate page/list/query key
from the regular `ProjectsPage`/`useProjects`, mirroring the backend keeping it a separate
endpoint (see `docs/collaboration.md`) rather than merging client-access Projects into the regular
owned+shared list. Opening a Project from here lands on the same `ProjectDetailPage` every other
role uses; `myRole: 'CLIENT'` already makes that page render view-only.

`AssetDetailPage`'s Client Notes card is the one thing a CLIENT role can write
(`useUpdateClientNotes`, gated on `myRole === 'CLIENT'`) — shown editable to the client, read-only
to everyone else who can view the Asset, and hidden entirely when there's nothing to show and the
viewer isn't the client (so it never appears as an empty, confusing card on every other Asset).

`NavLinks` also renders a dashed "Become a creator too" button under the nav list whenever
`isClientOnly` is true (`useUnlockCreatorMode`, `POST /users/me/creator-mode` — see
`docs/collaboration.md`). Unlike the automatic "owns something now" flip, this lets a client-only
account unlock the full UI immediately, with nothing owned yet. On success the mutation
invalidates (not `setQueryData`s) the `['currentUser', token]` query and *awaits* that refetch
before navigating to `/dashboard` — navigating first would race `ClientOnlyGate`, which would
still be reading the pre-refetch `isClientOnly: true` and bounce straight back to
`/client-projects`. Once the refetch lands, `getNavItems` switches over on its own and the button
removes itself — no local "hide myself" state to manage.

## Audio playback and presigned URLs

`AudioPlayer` never holds a permanent URL to an audio file. On first press of Play (or Download),
it requests a short-lived presigned URL from the backend (`GET /assets/{id}/file?download=...`,
see `docs/storage.md`) and only then sets it as the `<audio>` element's `src`. The `download` flag
is the caller's *declared intent* — the backend can't observe what the browser actually does with
a URL after issuing it, so this flag is what gets recorded as `ASSET_PLAYED` vs. `ASSET_DOWNLOADED`
in the analytics event log (see `docs/analytics.md`).

## Environment configuration

All configuration comes from Vite env vars (`VITE_*` prefix, read via `import.meta.env`) — nothing
is hardcoded and nothing secret is committed:

| File | Loaded by | Committed? |
|---|---|---|
| `.env.example` | nothing — documentation only | yes |
| `.env.development` | `npm run dev` | yes — contains only the local API URL and a Google OAuth Client ID, neither of which is a secret (the Client ID is a public identifier the backend verifies server-side, not a credential) |
| `.env.test` | Vitest | yes — points at a URL nothing ever actually calls, since every test request goes through MSW |
| `.env.production` | `npm run build` in a real deploy | **gitignored** — not created by this repo |

Required variables:

- `VITE_API_BASE_URL` — base URL of the Spring Boot API, no trailing slash.
- `VITE_GOOGLE_CLIENT_ID` — must match the backend's `google.oauth.client-id`. Only required for
  the "Sign in with Google" button; username/password auth works without it.

## CORS

The backend previously had no CORS configuration at all (it didn't need one before a browser
client existed). `SecurityConfig` now registers an explicit `CorsConfigurationSource` allowing only
the origin(s) listed in `cors.allowed-origins` (`application.properties`, defaulting to
`http://localhost:5173`, Vite's dev server port) — never a wildcard, including in production, where
it should be set to the deployed frontend's actual origin via the `CORS_ALLOWED_ORIGINS` env var.

## Testing

Not full end-to-end — Vitest + React Testing Library + MSW, mocking the network boundary the same
way the backend's own tests mock nothing and use a real test database (a deliberate difference:
the frontend has no database of its own to be honest about; the API boundary is the equivalent
seam). Coverage focuses on behavior that would be embarrassing to ship broken:

- `ProtectedRoute` — unauthenticated visitors get redirected; authenticated ones see the route.
- `LoginPage` — successful sign-in navigates to the dashboard; a backend error message is shown
  verbatim, not swallowed.
- `AssetsPage` — real API data renders; empty and error states show the right UI instead of a
  blank screen or a crash.
- `DashboardPage` — every number rendered comes from the (mocked) analytics response, never
  fabricated client-side.
- `AudioPlayer` — a presigned URL is fetched exactly once per play, and the `download` intent flag
  is set correctly depending on which control was pressed.
- `ClientFormDialog` — form submission, in-flight disabled state, and native `required` validation.
- `CollaboratorsPanel` — management controls are shown to an `OWNER` and hidden from `VIEW`/`EDIT`
  collaborators, driven entirely by `myRole`, per "Permission-aware UI" above.
- `ProjectDetailPage`/`AssetDetailPage` — asset-management and client-notes controls are shown or
  hidden correctly for every `myRole`, including the `CLIENT` regression coverage described above.
- `ClientProjectsPage` — lists `GET /projects/as-client`, proven distinct from the regular
  `GET /projects` list; empty state when nothing's been shared yet.
- `navItems`/`App`'s `ClientOnlyGate` — the right nav items for a client-only vs. dual-role vs.
  regular account, and that a client-only account is actually redirected away from producer-facing
  pages, not just missing the nav link to them.
- `NavLinks` — "Become a creator too" only renders for a client-only account, and clicking it
  (against a stateful pair of mock handlers standing in for the real backend) unlocks the full nav
  and removes the button, proving the component reacts to the refetched `/users/me` value rather
  than to the mutation's own response body.

One environment quirk worth recording: Node 22+ ships its own inert global `localStorage` (gated
behind a `--localstorage-file` flag) that shadows jsdom's real, working implementation during
tests. Vitest's jsdom environment only promotes a jsdom-provided global onto the test global scope
when Node doesn't already define that name — since Node already defines `localStorage`, jsdom's
copy is silently dropped and every `localStorage` call resolved to `undefined`. Fixed with a small
in-memory `Storage` implementation installed directly onto `globalThis` in
`src/test/localStorageMock.ts`, loaded before any other setup file.

## Running it

```
cd frontend
npm install
cp .env.example .env.development   # already committed with working local values — only needed if starting fresh
npm run dev          # dev server at http://localhost:5173
npm run build         # tsc -b && vite build -- type-checks, then produces dist/
npm run lint           # oxlint
npm test                # vitest run
```

The backend must be running separately (`./mvnw.cmd spring-boot:run` from the repo root) with
`cors.allowed-origins` including the frontend's dev server origin.
