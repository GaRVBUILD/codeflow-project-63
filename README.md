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

## Run with Docker

1. Copy `.env.example` to `.env` and replace the local passwords and JWT secret.
2. Run `docker compose up --build` from the repository root.
3. Open the UI at http://localhost:5173 and the API at http://localhost:8080.

The Compose setup enables development seed data. Sign in as `demo@codeflow.local` / `Demo1234!` for sample tasks and time logs, or `admin@codeflow.local` / `AdminDemo123!` for the admin dashboard. These are local development credentials only. For a non-demo deployment, set `APP_SEED_ENABLED=false`.

## Run locally

Create a PostgreSQL database named `codeflow` and set the variables in `.env.example` in your environment. Start the backend with `cd backend; mvn spring-boot:run`, then start the UI with `cd frontend; npm install; npm run dev`.

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
