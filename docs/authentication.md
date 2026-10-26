# Accounts and ownership — Milestone 16

Custerra uses Spring Security form authentication with server-side servlet sessions. Passwords use Spring's BCrypt encoder with work factor 12 and a unique generated salt. Usernames are case-insensitive, 3–40 ASCII letters/digits/underscores. Registration requires 12–72 characters and rejects passwords exceeding 72 UTF-8 bytes. No email address is required, and account recovery/email verification are not implemented.

## Session API

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/auth/csrf` | Returns `{headerName, token}` for the current session; may create an anonymous session |
| `POST /api/v1/auth/register` | JSON `{username,password}`; creates account, returns 201 identity; does not implicitly authenticate |
| `POST /api/v1/auth/login` | Form URL-encoded `username` and `password`; returns 204; invalid credentials return generic 401 |
| `GET /api/v1/auth/me` | Returns `{id,username}` or 401 |
| `POST /api/v1/auth/logout` | Invalidates session and clears session cookie; returns 204 |

Every unsafe request, including public route POSTs, registration, login and logout, requires `X-CSRF-TOKEN`. Retrieve a fresh token after login/logout because authentication rotates it. The frontend obtains a token before each mutation and never automatically retries a write. Session IDs remain in HttpOnly cookies; no authentication token is stored in localStorage. Login rotates the session ID to prevent fixation. Idle timeout is 30 minutes. CSRF uses Spring's default session repository and masked request token handling. Security errors use the common API error structure, with 401/403 rather than redirect loops.

The client sends credentials to the configured API. CORS allows only configured exact origins, credentialed requests, supported methods, and the CSRF header. Keep frontend and API on the same site (localhost ports are supported); unrelated-site deployments require an explicitly reviewed cookie/proxy design. Cookies use SameSite=Lax; set `SESSION_COOKIE_SECURE=true` with HTTPS outside local development. The default API remains loopback-bound.

Authentication endpoints have a bounded in-process throttle: 20 attempted login/registration POSTs per transport peer address per ten minutes, at most 10,000 active address windows. It does not trust forwarded headers. A reverse proxy needs an appropriately configured edge limiter; this local limiter is not distributed and users behind one peer share the limit. Sessions are in-memory and expire on server restart; shared persistent sessions are deployment work, not claimed here.

## Resource ownership and old data

Flyway V5 adds accounts and nullable `owner_id` columns to journeys and history snapshots. Newly created resources obtain their owner from the authenticated server principal, never from request JSON. Journey list/get/status/delete/check/notices and history create/get enforce ownership; guessed IDs belonging to someone else return 404. Public hazard/weather/risk/route planning APIs remain public, with CSRF protection on POSTs. Scheduler checks operate internally across owned active journeys and are never exposed as an unrestricted HTTP endpoint.

Existing installation-owned rows are retained with a null owner and are unavailable through account APIs. Existing unowned journeys are paused and excluded from scheduling. No first registrant automatically receives existing data. To recover old data, an installation operator must explicitly identify the intended account and resource IDs, back up the database, and assign those rows in an audited transaction. There is no public claim endpoint. Do not broadly assign all rows when multiple people have used an installation.

## Browser behavior

`/login` and `/register` provide account forms. Public origin/destination planning still works on `/routes`; saved journeys and `/history` require a session. A 401 clears private components and offers sign-in without navigation loops. Delayed session reads cannot restore an identity after session expiry. Signing out clears this browser's saved journey copies; storage errors are reported with a manual cleanup link. Milestone 17 also revokes the push subscription registered in that server session. A fresh login revokes the previous session subscription before account switching. Session expiry alone does not revoke durable push subscriptions; previously accepted messages may still arrive. See [notifications](notifications.md).

Offline copies remain explicit, read-only exports in the browser profile. They are not encrypted, and session expiry alone does not erase them: this preserves intentional offline access. Anyone with access to that browser profile can read them. Use sign-out or the Offline copies page to remove them. A disconnected tab cannot be remotely erased by the server.

## Verification and references

Full-stack database tests exercise registration/hashing, UTF-8 length bounds, case-insensitive uniqueness, CSRF, session rotation, logout, expired sessions, CORS, rate limiting, all cross-user journey operations, history isolation and unowned legacy data. An actual HTTP server test verifies HttpOnly/SameSite cookies, cache controls and CSRF rotation after login. Browser tests cover registration/login/logout, private view removal, offline cleanup, failed credentials and session expiry; existing provider and journey suites remain in place.

Implementation follows the [Spring Security form-login lifecycle](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html), [CSRF handling](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), and [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html). These are framework primitives; no custom token or password algorithm is used.

Milestone 20 adds session/CSRF-protected developer credential management under `/api/v1/developer`. API credentials themselves authenticate only `/api/v1/b2b` and cannot access consumer journeys, history or operations. The operations bearer token is a third, separate credential.
