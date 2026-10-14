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

## Tests

```bash
cd api && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw verify   # needs Docker (Testcontainers)
cd web && npm test
```

## Deploy

- API: push to `main` runs `.github/workflows/deploy-api.yml` (verify, build image, push to Docker Hub, `docker compose up -d` on the VPS). Health: https://api.asendus.alber.ee/api/v1/actuator/health
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
