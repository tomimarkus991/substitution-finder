# Asendus

A web app for a school substitution coordinator. She uploads the school's aSc teacher-timetable PDF, marks absent teachers for a date, picks eligible substitutes for each lesson, and copies or prints the day's substitution table. The UI is in Estonian.

## Repo layout

- `api/` - Spring Boot 4 API (Java 25, Maven, Postgres, Flyway, PDFBox). Context path `/api/v1`.
- `web/` - Vite + React + TypeScript SPA (Tailwind, react-query, Google sign-in).
- `docs/superpowers/` - design spec and implementation plan.
- `.github/workflows/` - API verify/deploy and web deploy.
- `docker-compose.prod.yml`, `.env.example` - production stack for the VPS.

## Local development

Docker must be running (Postgres starts automatically with the API).

API (dev profile, default Java is 21 so point at 25):

```bash
cd api
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Web:

```bash
cd web
cp .env.example .env.local
npm install
npm run dev
```

Open http://localhost:5173. In dev the login page shows a dev-only "Dev login" button that signs in as `dev@asendus.app` without Google. It does not exist in production builds.

## Dev container (sandbox for Claude Code)

`.devcontainer/` runs Java 25, Node 22 and Claude Code in Docker, with Postgres as a separate service. Claude runs as a non-root user with no sudo, so it can use `--permission-mode bypassPermissions`. Stop the API and web dev servers on your machine first, because the container uses the same ports (5173, 8080).

```bash
npm install -g @devcontainers/cli
devcontainer up --workspace-folder .

# Each in its own terminal
devcontainer exec --workspace-folder . bash -c 'cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev'
devcontainer exec --workspace-folder . bash -c 'cd web && npm run dev -- --host 0.0.0.0'
devcontainer exec --workspace-folder . bash   # then: claude --permission-mode bypassPermissions
```

There is no Docker inside the container, so the Testcontainers API tests (`./mvnw verify`) run on the host or in CI. After changing `.devcontainer/`, rebuild with `devcontainer up --workspace-folder . --remove-existing-container`.

The repo is mounted in, so Claude can change any file in it, including `.git/hooks`. Check `ls .git/hooks` before committing on the host. Outbound network is open.

## Tests

```bash
cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw verify   # needs Docker (Testcontainers)
cd web && npm test
```

## Deploy

- API: push to `main` runs `.github/workflows/deploy-api.yml` (build image with tests skipped, push to Docker Hub, `docker compose up -d` on the VPS). Tests run in `verify-api.yml` on pull requests; deploy does not run them. Health: https://api.asendus.alber.ee/api/v1/actuator/health
- Web: push to `main` touching `web/**` runs `.github/workflows/deploy-web.yml` (test, build, deploy to Cloudflare Pages project `asendus`).

### One-time manual setup

1. `cd ~/Documents/code/personal/infra && ./new-app.sh <owner/repo> asendus --dry-run`, then again without `--dry-run`.
2. Add `ALLOWED_EMAILS=iliarebla@gmail.com,tomimarkusalber@gmail.com` and `CORS_ALLOWED_ORIGINS=https://asendus.alber.ee` to the app env (`infra/apps/asendus.env`), then rerun `new-app.sh` so the `ENV_FILE` secret updates.
3. Google Auth Platform -> Clients -> the shared web client: add JavaScript origins `https://asendus.alber.ee` and `http://localhost:5173`. Audience -> Test users: add `iliarebla@gmail.com`.
4. Cloudflare: create a Pages project `asendus` (direct upload) and attach the custom domain `asendus.alber.ee`. In the GitHub repo add secrets `CF_API_TOKEN`, `CF_ACCOUNT_ID` and the variable `GOOGLE_CLIENT_ID`.

## Updating the timetable

1. Open **Tunniplaan** and upload the new teacher-timetable PDF.
2. Check the preview (teachers and lessons look right).
3. Click **Aktiveeri**. Earlier substitutions keep working; the old version stays in the history.
4. Flag placeholder entries (e.g. "Vaba" or unnamed teachers) once; the flag is kept for later uploads.