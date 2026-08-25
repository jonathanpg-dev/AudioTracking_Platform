# Production deployment & CI/CD (Phase 8)

## Architecture

```
                    Internet
                       |
                     HTTPS
                       |
              +--------v--------+
              | React Frontend  |   Cloudflare Pages (static, free)
              +--------+--------+
                       | HTTPS
                       |
              +--------v--------+
              | Spring Boot API |   Render Web Service (Docker, free tier)
              +------+-----+----+
                     |     |
                     |     +----------- Cloudflare R2 (already in use since Phase 4)
                     |
                     +----------------- Neon Postgres (free, serverless)
```

Three managed services, no servers to patch, no Kubernetes, no Terraform. GitHub Actions is the
only "infrastructure" this repo owns directly.

## Why this stack, at $0/month

| Component | Choice | Why |
|---|---|---|
| Frontend | Cloudflare Pages | Free, unlimited bandwidth (unlike Vercel's 100GB/mo cap), git-connected, automatic HTTPS, same account as R2. |
| Backend | Render (free Web Service, Docker) | No credit card required for the free tier, real Docker support, env-var secrets, automatic HTTPS. Trade-off: spins down after ~15 min idle, so the first request after idle takes 30-60s (cold start) — acceptable for a portfolio project with no real users yet; see "Scaling up" below. |
| Database | Neon (free Postgres) | A genuinely perpetual free tier (not a 30-day trial like Render's own Postgres offering), standard Postgres wire protocol — no code changes needed. |
| Storage | Cloudflare R2 (already in use) | Unchanged; production just needs its own bucket + token, isolated from the dev bucket. |
| CI/CD | GitHub Actions | Free at this scale for both public and private repos. |

Nothing here needs Terraform — every piece is a git-connected dashboard or a one-time API
token/deploy hook.

## Environment variables

Every value below is read from the environment — nothing production-sensitive is hardcoded
anywhere in the repo (see the security review at the bottom of this doc).

### Backend (Render)

| Variable | Example | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://ep-xxx.neon.tech/neondb?sslmode=require` | Must be the **JDBC** form (`jdbc:postgresql://...`), not the plain `postgres://` URI Neon's dashboard shows by default — see "Converting Neon's connection string" below. |
| `DATABASE_USERNAME` | `neondb_owner` | From Neon's connection details. |
| `DATABASE_PASSWORD` | *(secret)* | From Neon's connection details. |
| `JWT_SECRET` | *(secret, 64 hex chars)* | Generate with `openssl rand -hex 32`. **Do not reuse the local dev one** — see "Secrets" below. |
| `JWT_EXPIRATION_MS` | `3600000` | Optional; defaults to 1 hour. |
| `CORS_ALLOWED_ORIGINS` | `https://your-project.pages.dev` | The exact Cloudflare Pages URL (or custom domain later). Comma-separated if there's ever more than one. |
| `GOOGLE_OAUTH_CLIENT_ID` | *(the existing Client ID)* | Optional to set explicitly — it already defaults to the real value in `application.properties` since it isn't a secret. |
| `R2_ENDPOINT` | `https://<account-id>.r2.cloudflarestorage.com` | Production R2 endpoint. |
| `R2_ACCESS_KEY_ID` | *(secret)* | From a **new, production-only** R2 API token — see "R2 production setup" below. |
| `R2_SECRET_ACCESS_KEY` | *(secret)* | Same token. |
| `R2_BUCKET` | `audiotracking-platform-prod` | A separate bucket from dev, so a bug in one environment can't touch the other's files. |
| `PORT` | *(set automatically by Render)* | Do not set manually — Render injects this and expects the app to bind to it; `server.port=${PORT:8080}` already handles it. |

### Frontend (Cloudflare Pages / GitHub Actions build step)

| Variable | Example | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `https://audiotracking-platform.onrender.com` | The deployed Render backend's URL, no trailing slash. |
| `VITE_GOOGLE_CLIENT_ID` | *(the existing Client ID)* | Not a secret; hardcoded directly in `deploy.yml` since it never changes per environment. |

## Secrets

**Found during the Phase 8 audit:** `application.properties` had a hardcoded database password
and JWT signing secret, committed since this project's first two commits. The repo is private
(confirmed via the GitHub API), so this was never a public leak, but both values have been:

1. Removed from every file Git tracks — the only place either can now come from is an environment
   variable (`DATABASE_PASSWORD`, `JWT_SECRET`), with no default, so a misconfigured deployment
   fails loudly at startup instead of silently running with a placeholder.
2. Replaced locally with a fresh, randomly-generated JWT secret and moved into a **gitignored**
   `src/main/resources/application-local.properties` (see
   `application-local.properties.example` for the shape).

**Generate genuinely new values for production** — don't reuse the local dev database password or
the rotated local JWT secret for Render. Neon issues its own database password; generate a fresh
JWT secret with `openssl rand -hex 32`.

## Converting Neon's connection string

Neon's dashboard shows something like:

```
postgres://neondb_owner:AbC123@ep-cool-lab-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
```

Spring's `spring.datasource.url` needs the JDBC form instead:

```
jdbc:postgresql://ep-cool-lab-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
```

i.e.: swap `postgres://user:password@` for `jdbc:postgresql://`, and set `DATABASE_USERNAME`/
`DATABASE_PASSWORD` separately from the two credential fields that were in the original URI.

## Deployment process

### 1. Neon (database)

1. Create a free project at neon.tech.
2. Copy the connection string, convert it per the section above, and set the three `DATABASE_*`
   Render env vars.
3. **Nothing to run manually** — Flyway (`src/main/resources/db/migration`) creates the whole
   schema automatically the first time the app starts against this database. Don't run any SQL by
   hand against it; see "Migration safety" below.
4. Backups: Neon's free tier includes point-in-time restore for a rolling **24-hour** window,
   handled automatically — nothing to configure.

### 2. Cloudflare R2 (production bucket)

1. Create a **new** bucket, separate from whatever dev bucket already exists (e.g.
   `audiotracking-platform-prod`).
2. Create a new R2 API token scoped to just that bucket (Object Read & Write) — don't reuse the
   dev token in production, so revoking one doesn't affect the other.
3. Set `R2_ENDPOINT`/`R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`/`R2_BUCKET` on Render.
4. The bucket stays **private** — nothing here changes that. Audio access still only ever happens
   through the app's own presigned-URL flow (`docs/storage.md`), never a public bucket URL.

### 3. Render (backend)

1. Create a free Web Service, connect this GitHub repo, and set **Runtime: Docker** (it'll pick up
   the root `Dockerfile` automatically).
2. Set every backend env var from the table above.
3. Set the health check path to `/actuator/health` in the service settings.
4. **Turn OFF "Auto-Deploy"** in the service settings. Deploys are triggered only by
   `.github/workflows/deploy.yml` after CI passes (see "CI/CD" below) — leaving Render's own
   auto-deploy on would let a broken push reach production before tests ever ran.
5. Once created, copy the service's **Deploy Hook URL** (Settings > Deploy Hook) into the GitHub
   repo secret `RENDER_DEPLOY_HOOK_URL`.
6. Note the service's public URL (`https://<name>.onrender.com`) — that's `VITE_API_BASE_URL` for
   the frontend, and it also needs adding as an **Authorized JavaScript origin is NOT needed here**
   (that's the frontend's job, see Google OAuth below) — Render's URL only needs to reach Neon/R2/
   Google's verification endpoint outbound, nothing inbound to configure for it specifically.

### 4. Cloudflare Pages (frontend)

1. Create a Pages project (name it something short and memorable — that name is
   `CLOUDFLARE_PAGES_PROJECT_NAME`, a GitHub Actions **variable**, not a secret).
2. **Don't connect it to GitHub for auto-builds** — like Render, deploys happen only through
   `deploy.yml`, via `wrangler pages deploy`, after CI passes.
3. Generate a Cloudflare API token (My Profile > API Tokens > "Edit Cloudflare Workers" template
   covers Pages too) and find the Account ID (right sidebar of any Cloudflare dashboard page).
4. Add three GitHub repo secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and the repo
   **variable** `CLOUDFLARE_PAGES_PROJECT_NAME` (Settings > Secrets and variables > Actions —
   variables and secrets are separate tabs there).
5. Add the repo **variable** `VITE_API_BASE_URL` set to the Render URL from step 3 above.
6. `frontend/public/_redirects` already handles SPA fallback (`/dashboard`, `/assets/:id`, etc.
   resolving correctly on direct navigation instead of a 404) — nothing to configure for that.

### 5. Google OAuth (production origin)

This app's Google sign-in uses Google Identity Services' **ID-token model** — the frontend gets an
ID token directly from Google client-side and posts it to the backend for verification. There is
no `GOOGLE_CLIENT_SECRET` and no OAuth redirect/callback URI anywhere in this architecture (unlike
the classic Authorization Code flow the Phase 8 brief assumed) — only the origins allowed to
*request* a token need updating:

1. Google Cloud Console > APIs & Services > Credentials > (the existing OAuth 2.0 Client ID).
2. Under **Authorized JavaScript origins**, add the Cloudflare Pages URL (e.g.
   `https://your-project.pages.dev`).
3. Nothing on the backend changes — `google.oauth.client-id` is already the same value in every
   environment (it's the *audience* the backend verifies against, not a secret; see
   `application.properties`).

### 6. First deploy

Push to `master` (or merge a PR into it). `ci.yml` runs; if it's green, `deploy.yml` fires
automatically and deploys both halves. Watch the Actions tab for both workflows.

## CI/CD

- **`.github/workflows/ci.yml`** — runs on every PR and every push to `master`. Three parallel
  jobs: `backend` (real Postgres service container, `mvnw test` then `mvnw package`), `frontend`
  (`npm test` then `npm run build`), `docker` (builds the backend image, doesn't push — this is
  the only place the Dockerfile itself gets verified, since Docker isn't available in local dev
  here either). A failure in any job fails the whole workflow.
- **`.github/workflows/deploy.yml`** — triggered by `ci.yml` finishing, filtered to
  `conclusion == 'success'` and branch `master`. This is what makes "test -> build -> deploy" real
  rather than aspirational: nothing re-decides whether the code is good, it only acts on ci.yml's
  verdict for the exact commit CI just ran. Backend: one `curl` to Render's deploy hook (Render
  does its own Docker build server-side). Frontend: rebuilds with the real production
  `VITE_API_BASE_URL` baked in (Vite env vars are compile-time, so CI's own test build is useless
  for this) and deploys via `wrangler pages deploy`.
- **Permissions**: both workflows declare `permissions: contents: read` explicitly — nothing here
  needs to write to the repo, and a fork's `pull_request` run never receives repository secrets
  regardless (GitHub's own protection), so no PR can exfiltrate `RENDER_DEPLOY_HOOK_URL` or the
  Cloudflare token.

## Migration workflow

Flyway (`src/main/resources/db/migration`) owns the schema everywhere now — `ddl-auto=validate`
means Hibernate only ever checks its entity mappings against what Flyway already created, never
mutates the schema itself.

**Making a schema change from here on:**

1. Change the entity as usual.
2. Add a new `src/main/resources/db/migration/V2__description.sql` (never edit `V1__baseline.sql`
   or any already-applied migration file — Flyway checksums them and refuses to run if one
   changes after being applied).
3. Run the app locally (or the test suite) — Flyway applies it automatically on startup.
4. Deploy as normal; the same migration runs against Neon the moment the new version starts.

**`V1__baseline.sql`** was generated by letting Hibernate build the schema fresh against a
throwaway database (`ddl-auto=create`), then capturing it with `pg_dump --schema-only` — not
hand-written, so it can't drift from what the entities actually produce.
`spring.flyway.baseline-on-migrate=true` exists specifically so this could be adopted on top of
the dev/test databases' pre-existing (pre-Flyway) schema without trying to re-run `CREATE TABLE`
on tables that already exist; it's a no-op against any genuinely empty database (fresh CI, fresh
Neon project).

## Local development

Unchanged from `docs/frontend.md`/`docs/storage.md`, with one addition: copy
`src/main/resources/application-local.properties.example` to
`src/main/resources/application-local.properties` (gitignored) and fill in your local Postgres
credentials and a JWT secret (`openssl rand -hex 32`).

## Running tests / building locally

```
./mvnw test                       # backend: full suite against a local Postgres
./mvnw clean package -DskipTests  # backend: build the jar (same command the Dockerfile uses)

cd frontend
npm test                          # frontend: Vitest
npm run build                     # frontend: production build
```

## Building the Docker image locally

```
docker build -t audiotracking-platform .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/AudioTracking Platform \
  -e DATABASE_USERNAME=postgres \
  -e DATABASE_PASSWORD=... \
  -e JWT_SECRET=$(openssl rand -hex 32) \
  -e R2_ENDPOINT=... -e R2_ACCESS_KEY_ID=... -e R2_SECRET_ACCESS_KEY=... -e R2_BUCKET=... \
  audiotracking-platform
```

Docker isn't installed in the environment this was built in, so this exact command hasn't been
run there — `mvnw clean package -DskipTests` (the build stage's actual command) and running the
resulting jar directly with the same env vars *have* been verified there instead, which exercises
everything the Dockerfile does except the container layer itself. `ci.yml`'s `docker` job builds
the real image on every PR, which is the actual verification of the Dockerfile.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Render: "Web server failed to start. Port ... already in use" | Only ever a local dev issue — Render always assigns a free port via `PORT`. Locally, kill whatever's already on 8080. |
| Startup fails with "could not resolve placeholder 'DATABASE_URL'" | A required env var isn't set. Every genuinely secret value fails loudly rather than falling back to a default — check Render's env var list. |
| Flyway: "Validate failed: Migrations have failed validation... checksum mismatch" | A migration file already applied to that database was edited after the fact. Never edit an applied migration; add a new one instead. |
| 401 from `/actuator/health` | Check `SecurityConfig` still permits exactly `/actuator/health` — if this route ever 401s, the deployment platform will think the app is unhealthy and may restart it in a loop. |
| CORS errors in the browser console | `CORS_ALLOWED_ORIGINS` on Render doesn't exactly match the Cloudflare Pages URL (scheme + host, no trailing slash). |
| Frontend loads but every API call fails | `VITE_API_BASE_URL` wasn't set at *build* time — it's baked into the bundle, so redeploying with a corrected repo variable requires a fresh `deploy.yml` run, not just a Render restart. |
| Google sign-in fails only in production | The Cloudflare Pages URL wasn't added to Authorized JavaScript origins in Google Cloud Console. |

## Cost control

Everything above is $0/month at this project's current (effectively zero) traffic. What becomes a
real cost, and when:

| Component | Free tier limit | Becomes paid when... |
|---|---|---|
| Render (backend) | 750 instance-hours/month, spins down after ~15 min idle | Traffic is frequent/latency-sensitive enough that cold starts are unacceptable → Render's cheapest paid instance keeps it always-on (~$7/mo). |
| Neon (database) | Generous storage/compute on the free tier, scales to zero when idle | Sustained real traffic or data volume outgrows the free compute/storage allowance (Neon's own dashboard reports usage against the limit). |
| Cloudflare Pages | Unlimited bandwidth, unlimited requests | Effectively never, for a project this size — this is the one piece unlikely to need a paid tier at all. |
| Cloudflare R2 | 10GB storage + generous free egress/operations | Audio file storage or download volume grows past the free allowance. |
| GitHub Actions | 2,000 minutes/month (private repos) | This repo's CI run is a few minutes; would take heavy day-to-day commit volume to approach the limit. |

None of these require an architecture change to move to a paid tier later — every one is the exact
same managed service, just a different pricing plan, so "moving to paid infrastructure" is a
dashboard click, not a rewrite.

## Security review

- ✅ No secrets in Git — `DATABASE_PASSWORD`/`JWT_SECRET`/R2 credentials all env-var-only, no
  defaults; the previously-hardcoded values have been removed and the JWT secret rotated.
- ✅ No secrets in the Docker image — the multi-stage build never copies
  `application-local.properties` in (it's gitignored, so it isn't even in the build context; see
  `.dockerignore` as a second layer of defense regardless).
- ✅ No secrets in the frontend bundle — `VITE_API_BASE_URL`/`VITE_GOOGLE_CLIENT_ID` are both
  non-secret by design (a public URL and an OAuth audience value, never a credential).
- ✅ R2 stays private — production still only reachable through the app's own presigned-URL flow.
- ✅ Database not publicly reachable except through Neon's own authenticated, TLS-only connection
  string — the frontend never talks to Postgres directly (unchanged architecture: React → Spring
  Boot → Postgres).
- ✅ HTTPS everywhere — Render and Cloudflare Pages both provision it automatically; nothing here
  intentionally allows plain HTTP for auth or audio traffic.
- ✅ CORS restricted to the exact deployed frontend origin, never `*`.
- ✅ `/actuator/health` is the only unauthenticated route beyond `/api/v1/auth/**`, and exposes
  only the aggregate UP/DOWN status — no component-level detail (DB URL, disk paths), no stack
  traces, no config values.
- ✅ Authentication/authorization unchanged from Phase 5-7 — nothing about JWT verification,
  `ProjectAccessService`, or role checks was touched to make deployment easier.
- ✅ GitHub Actions least-privilege: `permissions: contents: read` on both workflows; deploy
  secrets are only ever readable by `deploy.yml`, which never runs against a PR/fork (workflow_run
  only fires for pushes to `master`).
