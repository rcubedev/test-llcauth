# Limelight Creatives Auth

Sign-in for every Limelight Creatives app, built with Java, Spring Boot and Thymeleaf.
People sign in with a 6-digit code sent to their email; the session is shared across every
`*.limelightcreatives.org` site.

A rewrite of the original Next.js app. It keeps the same database tables and the same contract
with the dashboard, so the dashboard works with it unchanged.

## Commands

| Command | What it does |
| --- | --- |
| `./gradlew bootTestRun` | Runs it at http://localhost:8080 with its own PostgreSQL: nothing to set up. Login codes are printed to the console. |
| `./gradlew bootRun` | The same, but using a PostgreSQL you run yourself (database `auth`, user and password `auth`) |
| `./gradlew test` | Runs the tests; they start their own PostgreSQL too (see [Tests](#tests)) |
| `./gradlew :build-logic:test` | Tests the Tailwind plugin |
| `./gradlew bootJar` | Builds `build/libs/auth.jar`, which runs with `java -jar` (the Dockerfile does this) |

Needs Java 25 (Gradle downloads it if you don't have it). The PostgreSQL that `bootTestRun` and the tests start is
the real thing (PostgreSQL 18, via embedded-postgres), downloaded through Gradle (just the build for your machine). It works on macOS (Apple Silicon or Intel),
Windows (x64) and Linux (x64 or ARM); on anything else, such as Windows on ARM, point the tests at your own
database with `TEST_DATABASE_URL` (see [Tests](#tests)). CI builds and tests on all three OSes. `bootTestRun` keeps its data in `build/dev-database` (`./gradlew clean` empties it).

## How other apps use it

This is the whole contract with the dashboard (and any other app):

- **Session cookie** `__Secure-authjs.session-token` (`authjs.session-token` locally, without HTTPS), on the
  domain `.limelightcreatives.org`, HttpOnly, SameSite=Lax. Apps don't read it; they pass it on.
- **`GET /api/permission`** with that cookie answers who is signed in:
  `{"authenticated":true,"id":"…","email":"…","name":"Sam Lee","role":"STAFF","needsProfile":false}`,
  or `401 {"authenticated":false}`. `name` is null until they've entered it (`needsProfile` is then true).
  Pages on our own subdomains can also call it from the browser (CORS).
- **`/login?next=<address>`** signs someone in and sends them back. `next` must be on limelightcreatives.org,
  otherwise they go to the dashboard. `&step=name` (sent by the dashboard) is accepted; the name form
  shows whenever a name is missing.
- **Signing out** is deleting the cookie, which the dashboard does itself.

## Settings (environment variables)

| Variable | Needed | What it is |
| --- | --- | --- |
| `DATABASE_URL` | yes | The PostgreSQL address. Neon's `postgresql://user:password@host/db?sslmode=require` works as is. Use the direct address, not the `-pooler` one. |
| `AUTH_SECRET` | yes | Signs session cookies. At least 32 characters: `openssl rand -base64 48`. Changing it signs everyone out. |
| `RESEND_API_KEY` | yes | For emailing codes. Without it, codes only go to the log. |
| `EMAIL_FROM` | no | Default `sign-in@auth.limelightcreatives.org` |
| `AUTH_COOKIE_DOMAIN` | no | Default `.limelightcreatives.org` |
| `AUTH_BASE_URL` | no | This app's address, default `https://auth.limelightcreatives.org`. Form posts must come from it. |
| `AUTH_ROOT_DOMAIN` | no | Where `next` may point and who may call the API from a browser. Default `limelightcreatives.org` |
| `AUTH_DEFAULT_REDIRECT` | no | Where to go after sign-in without a `next`. Default the dashboard. |

## Testing emails with Resend

Locally, codes are printed to the console unless you give it a Resend API key:

```
RESEND_API_KEY=re_... ./gradlew bootTestRun
```

A new Resend account can only send from `onboarding@resend.dev` (the local default) to the email you signed up
with. To email anyone, verify a domain in Resend (e.g. `auth.limelightcreatives.org`) and set `EMAIL_FROM`
to an address on it, as production does (`sign-in@auth.limelightcreatives.org`). If Resend refuses an email,
the log says why (e.g. the domain isn't verified). Locally, the code is then printed in the log with a warning
and the page goes on to the code box, so you can still sign in as anyone while testing; in production the page
says "Something went wrong" and codes are never logged (`auth.mail.log-code-if-unsent`).

After signing in locally you land on `/account`. To go back to a locally running dashboard instead, sign in
from it: it sends `?next=http://localhost:3000/...`. Cookies on `localhost` are shared between ports, so the
dashboard on 3000 sees the sign-in from 8080.

## Staff and admin accounts

Everyone who signs in for the first time is a `PARTICIPANT`. There are two ways to give someone more:

**Invite link** (for a group, e.g. mentors). Create a link in the database:

```sql
INSERT INTO "invite_links" ("id", "code", "role", "maxUses", "expiresAt")
VALUES (gen_random_uuid()::text, 'mentors-2026', 'STAFF', 30, now() + interval '14 days');
```

Then share `https://auth.limelightcreatives.org/login?invite=mentors-2026`. Whoever signs in through it
becomes `STAFF` (codes are case-insensitive; `maxUses` NULL means unlimited). Delete the row to stop the link
working; people who already joined keep their role. A link can never make an `ADMIN`, and never lowers
anyone's role.

**Directly**, for one person (and the only way to make an admin):

```sql
UPDATE "users" SET "role" = 'STAFF' WHERE "email" = 'someone@example.com';
```

Role changes apply on their next page load; nobody needs to sign in again.

## Switching over from the old app

1. Point it at the same database. On first start, Flyway sees the existing tables and records them as
   version 1 (`baseline-on-migrate`) instead of creating them, so all users, roles and invite links stay.
2. Use a new `AUTH_SECRET`. The old app's cookies are a different format, so everyone signs in once more.
3. Keep the same domain (`auth.limelightcreatives.org`) and `AUTH_COOKIE_DOMAIN`. The dashboard needs no changes.

## What's where

```
src/main/java/org/limelightcreatives/auth/
├── AuthApp, AuthProperties    ← startup and the auth: settings
├── DatabaseUrl                ← accepts Neon-style DATABASE_URLs
├── user/                      ← the users table and roles
├── login/                     ← SignIn (send/check codes), LoginCodes, RateLimits
├── invite/                    ← invite links
├── session/                   ← the signed session cookie
├── mail/                      ← Resend, or the log when there's no API key
└── web/                       ← the login page, /api/permission, redirect and cross-site checks
src/main/resources/
├── db/migration/              ← the schema (Flyway)
├── templates/login.html       ← every step of the sign-in page
└── static/                    ← fonts, favicon, js/login.js
src/main/css/app.css           ← Tailwind source (built by the plugin in build-logic/)
```

## Tests

`./gradlew test` starts its own PostgreSQL, so it needs nothing installed (no database, no Docker). To use a
database you already have instead, give it an empty one:

```
TEST_DATABASE_URL="jdbc:postgresql://localhost/auth_test?user=auth&password=auth" ./gradlew test
```

They cover the whole sign-in flow, the exact cookie and `/api/permission` responses the dashboard relies on,
invite rules (including two people racing for the last slot), rate limits, code guessing, forged cookies,
redirect checks, and starting up on a database created by the old app.

## Changes from the old app

- Session cookies are signed tokens (`v1.<user id>.<expiry>.<signature>`); role and name come from the
  database on every check, so a role change applies at once.
- Codes come from a secure random generator; each check takes one of the 5 attempts before comparing, so
  sending many guesses at once can't get more tries.
- `next` and CORS check the domain properly (`evil-limelightcreatives.org` no longer passes).
- Form posts from other sites are refused (Origin check), on top of SameSite cookies.
- The page works without JavaScript; `login.js` only disables buttons while a form submits.
- The visitor's IP for rate limits comes from the proxy's `X-Forwarded-For`, read the way Tomcat does it
  (only trusted proxies), not just the first value, which a client can fake.

## CI

`.github/workflows/build.yml` runs on every push and pull request:

- **Build and test**: `./gradlew build :build-logic:test`. The tests start their own PostgreSQL, so CI needs no
  database or secrets.
- **Docker image builds**: builds the `Dockerfile` (without publishing it), so a change that would break a
  deploy fails here first.

Deploys aren't in GitHub Actions: Vercel builds and deploys straight from GitHub (below).

## Deploying (Vercel + Neon)

Vercel runs the `Dockerfile` as a Vercel Function ([Container Runtime](https://vercel.com/docs/functions/runtimes/container),
in beta), in Sydney (`vercel.json`). Every push to `main` deploys to production; every other branch and pull request
gets its own preview deployment. For another host, the Dockerfile runs anywhere that runs containers.

**Cost.** Free on the Hobby plan for testing: Vercel only bills while a request is being handled, and scales to zero
after 5 minutes without traffic, so the next visit waits a few seconds while Spring Boot starts. Neon's free plan
sleeps the database after 5 minutes too, and gives each project 100 compute-hours a month. The database only sleeps
once the app has closed its idle connections, which it does after a minute (`spring.datasource.hikari` in
`application.yml`; `ConnectionPoolTest` keeps it that way).

### Database (Neon)

Create a **separate Neon project** in **AWS Sydney** (`ap-southeast-2`), next to the app, so the test copy never
touches real accounts. Copy its connection string with pooling off (the direct one).

### App (Vercel)

1. Add New > Project > import this GitHub repo. Vercel picks the Container preset from `vercel.json`.
2. Before deploying, add the **Environment Variables**:

   | Variable | Value |
   | --- | --- |
   | `PORT` | `8080` (Vercel sends traffic to port 80 unless told otherwise, and the app runs as a non-root user that can't use 80) |
   | `DATABASE_URL` | the Neon connection string |
   | `AUTH_SECRET` | `openssl rand -base64 48` (a different one from production) |
   | `AUTH_BASE_URL` | `https://<project>.vercel.app` |
   | `AUTH_ROOT_DOMAIN` | `<project>.vercel.app` |
   | `AUTH_COOKIE_DOMAIN` | `<project>.vercel.app` (if left unset, it's the real `.limelightcreatives.org`, which browsers reject here) |
   | `AUTH_DEFAULT_REDIRECT` | `https://<project>.vercel.app/account` |
   | `RESEND_API_KEY` | optional; without it, codes appear in the project's Logs |

   `<project>.vercel.app` is the production domain shown on the project page after the first deploy. If you only know
   it then, deploy, set the variables, and redeploy (Deployments > ... > Redeploy).

Then open `https://<project>.vercel.app/login`, sign in, and you land on `/account`. To make yourself staff, run the
SQL from [Staff and admin accounts](#staff-and-admin-accounts) in Neon's SQL editor.

Preview deployments get their own addresses, so the `AUTH_*` addresses above only match production. To sign in on a
preview, add the same variables with that preview's address for the Preview environment, or just test on `main`.

### Production

The real switch-over is the existing Vercel project for `auth.limelightcreatives.org`: set its framework to Container
(Settings > Build and Deployment, or this repo's `vercel.json`), add `PORT=8080`, `AUTH_SECRET` and `RESEND_API_KEY`,
set `DATABASE_URL` to the database's **direct** address (the old app's `DIRECT_URL`), and leave the other `AUTH_*`
variables unset (the defaults are the real addresses). The domain and DNS stay as they are.

Staging can't share a sign-in with the real dashboard: that needs it on `auth.limelightcreatives.org` with the shared
cookie domain, which is what production does.
