# Session Scheduler

Students register, verify their email, and request a proctored session. Supervisors get an email and a
platform notification, see every request, allot a proctor, and download the list as an Excel file.

Stack: Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, Thymeleaf, MySQL 8, Apache POI.

> Status: written and syntax-checked, but not yet compiled or run (the authoring environment had no access to
> Maven Central). Treat the first `docker compose up --build` as the first real build and report any error.

## What's in it

**Security (Spring Security)**
- Custom `/login` page (form login), accounts stored in the database, passwords hashed with BCrypt.
- `/register` for students, with email verification before the first login. Supervisors are seeded, not self-registered.
- Logout is a POST to `/logout` with the CSRF token. Every form carries a hidden `_csrf` token (Thymeleaf adds it to each `th:action` form).
- Public: `/`, `/login`, `/register`, `/verify`, `/error` and static files. Everything else needs a login; `/student/**` is students only, `/admin/**` and `/api/proctors/**` supervisors and admins only.
- `/account/password`: a logged-in user changes their password. It is not public, so it can't be opened after logout.
- Controllers read the current user with `@AuthenticationPrincipal UserDetails`.
- Logins are server-side sessions. With the `mysql` profile they are stored in MySQL (Spring Session JDBC), so a restart or Render spin-down doesn't log everyone out. The `local` profile keeps them in memory.

**Web layer (Spring MVC + Thymeleaf)**
- Post/Redirect/Get: booking, cancelling, assigning and changing a password all redirect after the POST, so a refresh never submits twice.
- After a failed booking the form and its errors are carried through the session (flash attributes) and the form refills. The password page carries only the messages, never the typed passwords.
- Forms bind with `th:object` / `th:field`; `th:inline="javascript"` passes server values to page scripts.
- JSON endpoints: `/api/timezones/regions` and `/api/timezones?region=` feed the Region → Time zone dropdowns on the booking form; `/api/proctors/available?sessionId=` marks busy proctors in the portal's assign dropdown.
- A friendly error page (`templates/error.html`) for 404, 403 and unexpected errors.

**Data (Spring Data JPA / Hibernate + MySQL)**
- Entities and repositories for users, verification tokens, proctors, sessions and notifications.
- MySQL schema changes are versioned with Flyway (`db/migration`). The `local` profile uses `ddl-auto: update` instead, so Hibernate creates and alters tables to match the entities.
- `DataInitializer` creates the first supervisor on startup and loads sample proctors when the table is empty (`APP_SEED_DEMO_PROCTORS=true`).
- Works with a hosted MySQL such as Aiven over SSL (see Deploy to Render).

**Configuration, logging, build**
- Settings bound from `application.yml` with `@ConfigurationProperties` (`AppProperties`), filled from environment variables in production.
- SLF4J logging; email failures are logged as warnings and never break a booking.
- Java 21, Spring Boot 3.5, Maven, multi-stage Dockerfile, `render.yaml` for auto-deploy on push to `main`.

## Quickest run: start the main class (no Docker, no MySQL)

Needs Java 21 and an IDE (IntelliJ IDEA, Eclipse or VS Code with the Java extensions).

1. Open the `session-scheduler` folder as a Maven project and let it download the libraries.
2. Run `SchedulerApplication` (the class with `main`).
3. Open http://localhost:8080. Supervisor login: `supervisor@example.com` / `ChangeMe123!`

With no profile chosen the app uses the `local` profile: an embedded database file under `./data`, and
emails are printed in the console instead of sent. After registering a student, copy the verification
link from the console into the browser.

To use MySQL and real email, start with the profile `mysql` (environment variable
`SPRING_PROFILES_ACTIVE=mysql`) and the settings listed further down. Docker Compose does this for you.

## Run the sandbox with Docker

Needs Docker only.

```bash
docker compose up --build
```

| What | Where |
|---|---|
| App | http://localhost:8080 |
| Test mail inbox (every email the app sends lands here) | http://localhost:8025 |
| MySQL | localhost:3306, database `scheduler`, user `scheduler` / `scheduler` |

Supervisor login for the sandbox: `supervisor@example.com` / `ChangeMe123!`
(set in `docker-compose.yml`; change it before anyone else uses the sandbox).

## Try the flow

1. Open the app, choose **Register**, and create a student account.
2. Open the mail inbox at http://localhost:8025 and click the verification link.
3. Log in as the student and book a session (name, date, time zone, time, duration, notes).
4. Log out, then log in as the supervisor. The request shows under Notifications and All sessions,
   and a "New session request" email is in the mail inbox.
5. Open **Proctors**, add a proctor, go back and assign them to the session.
   The student and the proctor each get an email.
6. Click **Download Excel** for the .xlsx of all sessions.

## Deploy to Render for free

| Part | Free service | Notes |
|---|---|---|
| App | Render web service, free plan | Sleeps after 15 min without traffic; keep it awake with a pinger (below). |
| Database | TiDB Cloud Starter | MySQL-compatible; free monthly quota per instance. |
| Email | Brevo, free plan, web API | 300 emails a day. Render's free plan blocks SMTP ports, the API uses normal HTTPS. |
| Keep-awake | cron-job.org or UptimeRobot | Calls `/login` every 10 minutes. One always-on service fits Render's 750 free hours a month. |

1. **TiDB Cloud**: create a Starter instance. In its SQL editor run `CREATE DATABASE scheduler;`.
   From **Connect** copy host, user and password. `DB_URL` is
   `jdbc:mysql://<host>:4000/scheduler?sslMode=VERIFY_IDENTITY`.
2. **Brevo**: create a free account. Under **Senders, domains & dedicated IPs** add and verify the sender
   address (it receives a code). For better delivery ask IT to add Brevo's domain records (DKIM/DMARC).
   Under **SMTP & API > API keys** create a key.
3. **Render**: **New > Blueprint**, pick the repo; `render.yaml` sets the free plan. Fill in the values it asks for.
4. When it's live, set `APP_BASE_URL` to the Render address and save (it redeploys).
5. **Keep-awake**: in cron-job.org (or UptimeRobot) add a job calling `https://<your-app>.onrender.com/login`
   every 10 minutes.

Paid setup instead (no sleeping, SMTP allowed): `plan: starter` in `render.yaml`, `APP_MAIL_PROVIDER=smtp`
and the `MAIL_*` settings; any MySQL host works (`?sslMode=REQUIRED` for hosted ones).

## Run without Docker

Needs Java 21, Maven and a MySQL database named `scheduler`.

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_URL='jdbc:mysql://localhost:3306/scheduler?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER=scheduler DB_PASSWORD=scheduler
export APP_ADMIN_EMAIL=supervisor@example.com APP_ADMIN_PASSWORD='ChangeMe123!'
mvn spring-boot:run
```

`mvn test` runs the whole flow (register, verify, book, assign, export) against an in-memory database.

## How it works

**Students** register (email confirmation required), sign in, and book their test time: date, start time and
US time zone (Eastern, Central, Mountain, Arizona, Pacific, Alaska, Hawaii; daylight saving is automatic).
They see their upcoming and past bookings and can cancel. They never see or hear about proctors.

**Supervisors** see every booking with its proctor status (**Proctor assigned** or **Not assigned**), assign
proctors, and download Excel. Assigning a proctor sends no email; it shows in the portal's Activity list.

**Emails sent** (nothing else is emailed; proctors never get email):

| Email | To | When |
|---|---|---|
| New test booking | every active supervisor | a student books a time |
| We've received your request | the student | a student books a time |
| Confirm your email | the student | once, at registration |
| Reset your password | whoever asked | only after "Forgot password?" |

## Supervisors and passwords

- The first supervisor is created on startup from `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` (only if it doesn't exist yet).
- More supervisors are added by hand on the **Supervisors** page: name, login ID (work email) and password.
  No email is sent; share the login with them. The same page sets a new password for a supervisor, and
  deactivates or reactivates accounts. You can't deactivate yourself.
- Everyone can change their own password under their name > Change password.
- **Forgot password?** on the sign-in page emails a reset link (valid 1 hour).
- `APP_SESSION_MINUTES` (default 60) is how long a test is assumed to take, used to stop a proctor being booked twice at once.

## Emails

All emails share one branded layout, `src/main/resources/templates/mail/message.html` (open it in a browser
to see a sample). The wording of each email is in `AccountService` and `BookingService`, built with
`MailMessage`. Each email is sent as HTML with a plain-text copy.

In local mode nothing is sent: the text is printed in the console and the HTML is saved to
`data/mail-preview/`, so you can open it and see exactly what the recipient would get.

## Settings (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | MySQL connection | local `scheduler` database |
| `MAIL_HOST`, `MAIL_PORT` | SMTP server | `localhost:1025` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP login | empty |
| `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` | set both to `true` for Google Workspace / Microsoft 365 (port 587) | `false` |
| `APP_BRAND_NAME` | product name shown in the app, page titles and emails | `Session Scheduler` |
| `APP_MAIL_FROM` | sender address | `no-reply@localhost` |
| `APP_MAIL_FROM_NAME` | sender name shown in the inbox | the brand name |
| `APP_MAIL_PROVIDER` | `smtp` (the MAIL_* settings) or `brevo` (Brevo web API) | `smtp` |
| `BREVO_API_KEY` | Brevo API key, when the provider is `brevo` | none |
| `APP_MAIL_LOG_ONLY` | `true` prints emails instead of sending them (default in local mode) | `false` |
| `APP_BASE_URL` | public address used in verification links | `http://localhost:8080` |
| `APP_DISPLAY_ZONE` | time zone of the supervisor portal and Excel export | `Asia/Kolkata` |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_NAME` | first supervisor, created on startup if missing | none |

## How it is organised

```
src/main/java/com/sessionscheduler/scheduler
  config/   security rules, app settings, first-supervisor seeding
  model/    users, verification tokens, proctors, exam sessions, notifications
  repo/     database access
  service/  accounts + email verification, booking + assignment, email, Excel export
  web/      pages: public, student, supervisor
src/main/resources
  db/migration/V1__init.sql   MySQL schema (managed by Flyway; add V2__..., never edit V1)
  templates/                  Thymeleaf pages
```

Session start times are stored in UTC together with the student's time zone, so each side sees the right
local time.

## Not built yet

- "Resend verification email" for students (registering again with the same email sends a new link).
- Reminder emails before a session, reschedule, and booking cut-off rules.
- Filters, search and paging on the supervisor list.
- Live push notifications (the badge refreshes every 30 seconds).

## When you scale

- Keep the app stateless: store HTTP sessions in Redis (Spring Session) so several copies can run behind a load balancer.
- Move email to a queue or outbox table so nothing is lost if the mail server is down.
- Add paging to the session list and export once there are thousands of rows.
- Upgrade to the current Spring Boot major version once the build is green.
#   S c h e d u l e r  
 #   S c h e d u l e r  
 