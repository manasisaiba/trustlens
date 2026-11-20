# TrustLens: AI Customer Experience Doctor

> Your AI customers. Your toughest critics. Your website improvement agent.

A business owner enters their website URL. TrustLens:

1. Generates 4 customer personas for the business category (Claude).
2. Runs each persona through the **real website in real Chromium** (Playwright). Claude picks one permitted action at a time from what the browser shows.
3. Records evidence for every step: URL, action, observation and screenshot.
4. Writes an audit report from that evidence. Every finding must cite recorded evidence steps, and findings that don't are discarded.
5. Generates an improved page preview (HTML/CSS), rendered in a sandboxed iframe next to a screenshot of the original.
6. Lets the owner approve or reject each proposed change, then export the HTML. Nothing is ever published to the business's site.

## Architecture

```
            ┌──────────── nginx :80 (only public port) ────────────┐
            │  /            → frontend (Next.js 15, Tailwind)       │
            │  /api/*       → backend  (Spring Boot 3, Java 21)     │
            └───────────────────────────────────────────────────────┘
backend ── JPA/Flyway ──► PostgreSQL 16             (internal network)
backend ── HTTP ────────► browser worker (Node + Playwright/Chromium, internal)
backend ── HTTPS ───────► Anthropic Claude API
worker  ── writes ──────► screenshots volume ◄── backend serves /api/screenshots (read-only)
```

| Folder | What |
|---|---|
| `backend/` | REST API, async audit workflow, Claude integration (`ai/`), agent loop (`service/TestingOrchestrator`), report and preview generation |
| `backend/src/main/resources/prompts/` | 5 separate prompts, each with a JSON output schema: persona generation, browser action planning, journey (observation) analysis, audit findings, website improvement |
| `worker/` | Browser worker. It exposes only `navigate_to_page, inspect_page, click_element, fill_form, scroll, take_screenshot, extract_visible_text, record_current_url` |
| `frontend/` | Landing, onboarding, audit dashboard, personas, live testing view, report, side-by-side preview, approval |
| `frontend/public/sample-site/` | **Fictional** "Bright Dental Clinic" site with built-in UX friction, for reliable demos (`/sample-site/index.html`) |
| `e2e/full-flow.spec.js` | Real Playwright test that drives the TrustLens UI end to end |
| `infra/nginx/` | Reverse proxy and rate limiting |

### How the agent loop works
1. The worker opens the site and labels visible interactive elements (`e1…eN`). It returns the URL, title, visible text, elements and a screenshot.
2. The backend sends the persona, task, history, page data and screenshot to Claude (`browser_action` prompt). Claude must answer through a forced tool call that matches a JSON schema.
3. The output is **validated before anything runs**: the action must be in the allowlist, the element must exist on the page, and the action limit forces `finish`.
4. The worker runs that one action. The backend saves an evidence row and loops until the journey finishes, hits the action limit (default 8) or times out (default 180 s).
5. An **independent** Claude call (`journey_analysis`) classifies the outcome from the evidence log, so the planner can't mark its own work as a success.

### Safety controls
- URLs are validated in **both** the backend and the worker: http(s) only, no credentials in the URL, and no localhost, private, link-local, CGNAT or metadata IPs (checked after DNS resolution and again on every request).
- Navigation is locked to the target site. External links and redirects are blocked.
- **Every non-GET request is aborted** in the browser, so forms, bookings, payments and messages cannot be sent. `tel:`/`mailto:` links are recorded but not activated. Popups, downloads, dialogs and service workers are blocked.
- Form filling uses synthetic test data only, and password and file inputs are skipped.
- Limits: actions per journey, journey timeout, concurrent sessions, idle-session cleanup and Nginx rate limits on the expensive endpoints.
- Generated preview HTML is sanitised (jsoup allowlist: no scripts, event handlers or `javascript:`; forms forced to `action="#"`). It is served with a strict CSP (`default-src 'none'; sandbox`) and shown in an `<iframe sandbox="">`.
- Postgres, the worker and the backend sit on an internal Docker network. Only Nginx publishes a port.

### Honesty rules built in
- No `ANTHROPIC_API_KEY` → the UI shows a configuration error and offers **Demo mode**, which is labelled everywhere. In Demo mode, personas come from templates and a keyword heuristic picks the clicks, **but the browser test is still real**.
- Findings are labelled "Simulated journey". The score is explained as a heuristic, not a satisfaction metric. No review data is used, and the report says so.
- If the preview needs facts that weren't found on the site, it shows `[Owner to verify: …]` instead of inventing them.

## Deploy on the GoDaddy VPS (Ubuntu)

```bash
# 1. Install Docker (once)
curl -fsSL https://get.docker.com | sh

# 2. Get the code onto the server (git clone, or scp the zip), then:
cd trustlens
cp .env.example .env
nano .env          # set POSTGRES_PASSWORD, ANTHROPIC_API_KEY, PUBLIC_URL=http://<your-ip-or-domain>

# 3. Build, start and smoke-test
./deploy.sh
```

Open `http://<your-vps-ip>/`. To update later, `git pull && ./deploy.sh`.
Logs: `docker compose logs -f backend worker`.

**HTTPS (optional, recommended):** point a domain's A record at the VPS, then put Caddy or certbot in front, or add a 443 server block to `infra/nginx/nginx.conf`.

**Sizing:** 4 personas run in parallel, each in its own Chromium context. The worker container has `shm_size: 1gb`. A 4 CPU / 8 GB VPS handles a couple of simultaneous audits comfortably. Reduce `PARALLEL_JOURNEYS` if memory is tight.

### Environment variables
| Var | Default | Notes |
|---|---|---|
| `ANTHROPIC_API_KEY` | (empty) | Required for Live mode |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` | Any Messages-API model that supports tool use and vision |
| `POSTGRES_PASSWORD` | (none) | Required |
| `MAX_ACTIONS_PER_JOURNEY` | 8 | Browser actions per persona |
| `JOURNEY_TIMEOUT_SECONDS` | 180 | Per persona |
| `PARALLEL_JOURNEYS` | 4 | Concurrent personas |
| `ALLOW_PRIVATE_TARGETS` | false | Dev only. Allows testing localhost |

## Local development (without Docker)

```bash
# Postgres on :5432 with db/user/password "trustlens"
cd worker   && npm install && npx playwright install chromium && ALLOW_PRIVATE_TARGETS=true npm start   # :4000
cd backend  && ALLOW_PRIVATE_TARGETS=true mvn spring-boot:run                                           # :8080
cd frontend && npm install && npm run dev                                                               # :3000
```

## Tests
```bash
cd backend && mvn test                     # URL guard, sanitiser, demo planner, validation of AI actions
cd worker  && npm test                     # private-IP / site-lock rules
BASE_URL=http://localhost:3000 node e2e/full-flow.spec.js   # real browser E2E through the whole UI
./scripts/smoke-test.sh http://localhost   # API-level smoke test (also run by deploy.sh)
```

## API
`POST /api/businesses` · `GET /api/businesses/{id}` · `GET /api/businesses/{id}/audits` · `GET /api/audits` · `POST /api/audits` · `GET /api/audits/{id}` · `POST /api/audits/{id}/generate-personas` · `POST /api/audits/{id}/start-testing` · `GET /api/audits/{id}/progress` · `GET /api/audits/{id}/report` · `POST /api/audits/{id}/generate-preview` · `GET /api/audits/{id}/preview` · `POST /api/previews/{id}/approve` · `POST /api/previews/{id}/reject` · `PATCH /api/previews/{id}/changes/{changeId}` · `GET /api/previews/{id}/render` · `GET /api/previews/{id}/export` · `GET /api/screenshots/{audit}/{file}` · `GET /api/system/status`

Long-running steps return `202 Accepted` and run asynchronously. The UI polls `/progress`.

## Known limitations (MVP)
- Sites that block headless browsers (Cloudflare challenges, etc.) produce a "could not load" result rather than a test.
- Each journey is limited to the configured number of actions, so very deep journeys can come back "partial".
- Publishing to the real website is out of scope. Export the HTML and hand it to the web team.
- Real customer-review import is not implemented. The report labels every finding as simulated.
