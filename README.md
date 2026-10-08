# CodeFlow

CodeFlow is a deadline and importance based task prioritization and focus tracking app. It combines a React interface with a Java 21/Spring Boot REST API and PostgreSQL.

## Features

- Personal account registration and login with BCrypt password hashing and signed JWTs.
- Forgot-password flow with single-use, 30-minute email reset links and request limiting (requires SMTP configuration).
- Private task CRUD with search, status updates, importance, category and deadlines.
- Server-side priority scores recalculated using admin configured deadline and importance weights.
- Priority board, dashboard, completion and overdue summaries, and tracked-time analytics.
- Persistent timer sessions, with one active timer per user.
- Admin area with a user activity table, category add/edit/delete, prioritization rule controls, tracked-hour and task trend charts, workload distributions, and JVM uptime/memory indicators.

## Run with Docker (recommended)

### Requirements

- Docker Desktop (Windows/macOS) or Docker Engine with the Docker Compose v2 plugin (Linux).
- Git, to clone this repository.

### Start the application

Run these commands from the repository root. Docker Compose reads the `.env` file automatically.

**PowerShell (Windows):**

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up --build -d
docker compose ps
```

**macOS/Linux:**

```bash
cp .env.example .env
# Edit .env and replace the example database password and JWT secret.
docker compose up --build -d
docker compose ps
```

Wait for the `db`, `backend`, and `frontend` services to start, then open:

- Web app: http://localhost:5173
- Backend API: http://localhost:8080/api

To follow service logs, run `docker compose logs -f backend frontend`. Press `Ctrl+C` to stop following logs. Stop the stack with `docker compose down`; this keeps the PostgreSQL data volume so your data is still there next time.

The development Compose configuration enables seed data. Sign in with `demo@codeflow.local` / `Demo1234!` to see sample tasks and time logs, or `admin@codeflow.local` / `AdminDemo123!` to open the admin dashboard. These accounts are for local development only. The current Compose file sets `APP_SEED_ENABLED=true`; before adapting the Compose setup for a non-demo deployment, change that value to `false` and replace all development secrets and passwords.

## Run manually without Docker

### Requirements

- Java 21 and Maven.
- Node.js and npm.
- PostgreSQL 16 running locally.

### 1. Create the database

In PostgreSQL, create a database and local user. For example, run the following as a PostgreSQL administrator in `psql`:

```sql
CREATE USER codeflow WITH PASSWORD 'replace-with-a-local-password';
CREATE DATABASE codeflow OWNER codeflow;
```

### 2. Start the backend

Open a terminal at the repository root and set the connection values for that terminal. The commands below use PowerShell; replace the database password and JWT secret with your local values.

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/codeflow"
$env:DATABASE_USERNAME = "codeflow"
$env:DATABASE_PASSWORD = "replace-with-a-local-password"
$env:JWT_SECRET = "replace-with-a-random-secret-at-least-32-characters"
$env:CORS_ORIGIN = "http://localhost:5173"
$env:APP_SEED_ENABLED = "true"
Set-Location backend
mvn spring-boot:run
```

Keep this terminal running. Hibernate creates or updates the local tables when the backend starts. Setting `APP_SEED_ENABLED` to `true` creates the development demo accounts and sample data.

### 3. Start the frontend

Open a second terminal at the repository root:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open http://localhost:5173. The frontend uses http://localhost:8080/api for the backend by default. Press `Ctrl+C` in each terminal to stop the frontend and backend.

For non-PowerShell shells, export the same environment variables before running `mvn spring-boot:run`, for example `export DATABASE_URL=jdbc:postgresql://localhost:5432/codeflow` and `export APP_SEED_ENABLED=true`.

## Stack and structure

- `backend`: Spring Web, Security, Data JPA, Jakarta Validation, JWT, PostgreSQL.
- `frontend`: React, Vite, React Router, Axios, Tailwind CSS, Recharts, Lucide.
- `docker-compose.yml`: frontend, backend, and PostgreSQL services.

The API is rooted at `/api`. Authentication routes are `/auth/register`, `/auth/login`, and `/auth/me`. Authenticated task routes are `/tasks` and `/tasks/{id}`; timer routes are `/tasks/{id}/timer/start`, `/tasks/{id}/timer/stop`, and `/tasks/{id}/time-logs`. Progress is available from `/progress`, `/progress/daily`, and `/progress/weekly`. Admin routes use `/admin` and require an ADMIN JWT role: `/admin/users`, `/admin/metrics`, `/admin/categories`, and `/admin/rules`.

### Password recovery email

Password reset is disabled until an SMTP sender is configured. Copy `.env.example` to `.env`, set `MAIL_ENABLED=true`, then provide `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and a real `MAIL_FROM` address accepted by your provider. Recreate the stack with `docker compose up --build -d`. Reset links expire after 30 minutes, are single-use, are stored as hashes, and reset requests are rate-limited. Password resets invalidate existing JWT sessions. With mail disabled, the UI reports that recovery email is not configured; it never pretends an email was sent.

## Priority calculation

Importance is normalized onto 0–1 from the user supplied 1–5 rating. Deadline urgency is bounded from 0 to 1 and increases as the due time approaches; overdue tasks have urgency 1 and tasks with no deadline have urgency 0. The backend combines these values using active weights (default 60% deadline and 40% importance), assigns LOW/MEDIUM/HIGH/CRITICAL bands, and gives completed tasks a score of zero.

## Development notes

Tables are created/updated by Hibernate for a local student project. Seeding runs only when `APP_SEED_ENABLED=true`; it is idempotent and creates the demo accounts, categories, tasks, rules, and time logs. Development mode uses environment fallbacks for the local database; replace them before exposing a deployment. Add migrations, refresh-token/revocation handling, richer filter/sort controls, integration tests and production secrets management before production use.
