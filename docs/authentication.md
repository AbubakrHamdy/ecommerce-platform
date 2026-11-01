# Authentication

Stateless JWT authentication with rotating refresh tokens, role-based access and email password reset.

- [How it works](#how-it-works)
- [Endpoints](#endpoints)
- [Tokens](#tokens)
- [Password reset](#password-reset)
- [Security decisions](#security-decisions)
- [Error responses](#error-responses)
- [Configuration](#configuration)
- [Try it locally](#try-it-locally)
- [Tests](#tests)

## How it works

The API keeps no server-side session. After login, the client sends a short-lived **access token** (a signed JWT) with every request, and swaps a long-lived **refresh token** for a new pair when the access token expires.

```mermaid
sequenceDiagram
    participant C as Client
    participant A as API
    participant DB as PostgreSQL

    C->>A: POST /auth/login {email, password}
    A->>DB: find user, verify BCrypt hash
    A->>DB: store SHA-256 hash of new refresh token
    A-->>C: accessToken (15 min) + refreshToken (7 days)

    C->>A: GET /users/me  (Authorization: Bearer accessToken)
    A->>A: verify JWT signature, issuer, expiry
    A-->>C: 200 profile

    Note over C,A: 15 minutes later the access token expires
    C->>A: POST /auth/refresh {refreshToken}
    A->>DB: revoke old refresh token, store new one
    A-->>C: new accessToken + new refreshToken
```

Every request passes through Spring Security's filter chain before it reaches a controller:

| Path | Access |
|---|---|
| `/api/v1/auth/**`, Swagger UI, `/v3/api-docs/**` | Public |
| `GET /api/v1/products/**`, `GET /api/v1/categories/**` | Public (browse without an account) |
| Everything else | Valid access token required |
| Admin endpoints | `@PreAuthorize("hasRole('ADMIN')")` on the method |

## Endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | `201` token pair |
| `POST` | `/api/v1/auth/login` | Public | `200` token pair |
| `POST` | `/api/v1/auth/refresh` | Public | `200` new token pair |
| `POST` | `/api/v1/auth/logout` | Public | `204` |
| `POST` | `/api/v1/auth/forgot-password` | Public | `202` (always) |
| `POST` | `/api/v1/auth/reset-password` | Public | `204` |
| `GET` | `/api/v1/users/me` | Bearer token | `200` profile |

**Register**

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "Secret123!",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

Login, register and refresh all return the same shape:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJlY29tbWVyY2UtcGxhdGZvcm0i...",
  "refreshToken": "mZq3Vd8rT0kXH1u8b2YtQe4lN7cWfJpA9sGx6yKiR5o",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Current user**

```http
GET /api/v1/users/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

```json
{
  "id": 2,
  "email": "customer@example.com",
  "firstName": "Jane",
  "lastName": "Customer",
  "role": "CUSTOMER",
  "createdAt": "2026-10-25T21:18:03.378Z"
}
```

## Tokens

### Access token (JWT, 15 minutes)

Signed with HMAC-SHA256 and validated by Spring Security's OAuth2 Resource Server (no hand-written JWT filter).

```json
{
  "iss": "ecommerce-platform",
  "sub": "2",
  "email": "customer@example.com",
  "roles": ["CUSTOMER"],
  "iat": 1761500000,
  "exp": 1761500900
}
```

The payload is readable by anyone but cannot be changed: editing `"CUSTOMER"` to `"ADMIN"` breaks the signature and the request gets `401`. The `roles` claim is mapped to `ROLE_CUSTOMER` / `ROLE_ADMIN` authorities, so `hasRole('ADMIN')` works on any endpoint.

### Refresh token (opaque, 7 days)

A random 256-bit string, not a JWT, so it can be revoked. Only its SHA-256 hash is stored in `refresh_tokens`; a leaked database cannot be used to sign in.

| Event | What happens |
|---|---|
| **Refresh** | The old token is revoked and a new pair is issued (rotation). Each refresh token works once. |
| **Reuse of a revoked token** | Treated as theft: every session of that user is revoked and the request gets `401`. The real user signs in again; the attacker is locked out. |
| **Logout** | The token row is deleted (not revoked), so a later replay is just "invalid" and does not trigger the theft response. |
| **Password reset** | All of the user's refresh tokens are revoked (signed out everywhere). |

```java
// AuthService.refresh
if (stored.isRevoked()) {
    refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
    throw new InvalidTokenException("Refresh token was already used; all sessions have been signed out");
}
if (stored.isExpired()) {
    throw new InvalidTokenException("Refresh token has expired");
}
stored.setRevoked(true);
return issueTokens(stored.getUser());
```

The method is annotated `@Transactional(noRollbackFor = InvalidTokenException.class)` so that "revoke all sessions" is committed even though an exception is thrown right after it.

## Password reset

```mermaid
sequenceDiagram
    participant U as User
    participant A as API
    participant M as Mail server

    U->>A: POST /auth/forgot-password {email}
    A-->>U: 202 Accepted (same answer for unknown emails)
    A->>M: (only if the account exists) email with one-time link
    U->>A: POST /auth/reset-password {token, newPassword}
    A->>A: token unused and < 30 min old?
    A-->>U: 204, password changed, all sessions signed out
```

- The token is random, single-use, valid for 30 minutes and stored only as a SHA-256 hash.
- Requesting a new link invalidates any earlier unused links.
- The email is sent asynchronously (`@Async`), so the endpoint responds immediately.
- In development every email is captured by **Mailpit** at <http://localhost:8025>; nothing is delivered to real inboxes.

## Security decisions

| Decision | Why |
|---|---|
| Passwords hashed with **BCrypt** | One-way and deliberately slow, so leaked hashes are impractical to crack. |
| Refresh and reset tokens stored as **SHA-256 hashes** | Database access alone is not enough to hijack a session. |
| Login returns the **same message** for unknown email and wrong password | Attackers cannot discover which emails are registered. |
| Login runs a BCrypt check **even when the email does not exist** | Response time does not reveal whether an account exists. |
| `forgot-password` **always returns 202** | Same reason: no account enumeration. |
| **Stateless** sessions, CSRF disabled | Tokens travel in the `Authorization` header, not cookies, so CSRF does not apply. |
| JWT secret comes from the **`JWT_SECRET` environment variable** | No production secret in the repository; startup fails if it is missing or shorter than 256 bits. |
| **401 and 403 return JSON** in the same format as every other error | Security errors are raised in filters, before controllers, so they are forwarded to `GlobalExceptionHandler` by `SecurityErrorHandler`. |

## Error responses

Every error, including those raised by Spring Security, uses one format:

```json
{
  "timestamp": "2026-10-25T22:15:14.476Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login"
}
```

Validation errors add `fieldErrors`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": [
    { "field": "email", "message": "must be a well-formed email address" },
    { "field": "password", "message": "size must be between 8 and 100" }
  ]
}
```

| Status | When |
|---|---|
| `400` | Validation failed, malformed JSON, bad parameter |
| `401` | Missing, invalid, tampered or expired token; wrong credentials; invalid refresh/reset token |
| `403` | Signed in but not allowed (e.g. a customer calling an admin endpoint) |
| `404` | Unknown resource or path |
| `409` | Email already registered |

## Configuration

`application.properties`:

| Property | Default | Notes |
|---|---|---|
| `app.jwt.secret` | `${JWT_SECRET}` | Base64, at least 32 bytes. Only the `dev` profile ships a value. |
| `app.jwt.issuer` | `ecommerce-platform` | Checked on every request. |
| `app.jwt.access-token-ttl` | `15m` | |
| `app.jwt.refresh-token-ttl` | `7d` | |
| `app.password-reset.token-ttl` | `30m` | |
| `app.password-reset.reset-url` | `${FRONTEND_URL:http://localhost:4200}/reset-password` | Link in the email; the token is appended as `?token=`. |
| `spring.mail.host` / `port` | `${MAIL_HOST:localhost}` / `${MAIL_PORT:1025}` | Mailpit in development. |

Generate a production secret with:

```bash
openssl rand -base64 48
```

## Try it locally

1. `docker compose up -d` (PostgreSQL, Redis, Mailpit).
2. Run the app with the `dev` profile.
3. Open Swagger UI at <http://localhost:3330/swagger-ui.html>.
4. Call `POST /api/v1/auth/login` with a seed account:

   | Role | Email | Password |
   |---|---|---|
   | Admin | `admin@example.com` | `Admin@123` |
   | Customer | `customer@example.com` | `Customer@123` |

5. Copy `accessToken`, click **Authorize** (top right) and paste it.
6. Call `GET /api/v1/users/me`.
7. Call `POST /api/v1/auth/forgot-password` and open <http://localhost:8025> to see the email.

## Tests

`AuthServiceTest` and `PasswordResetServiceTest` (JUnit 5 + Mockito) cover:

- registration normalizes the email, hashes the password, assigns `CUSTOMER` and rejects duplicates
- only the hash of the refresh token is stored
- wrong password and unknown email produce the same error, and both run a password check
- refresh rotates the token; reuse revokes every session; expired tokens are rejected
- logout deletes the token instead of revoking it
- password reset ignores unknown emails, invalidates older links, burns the token, and signs the user out everywhere

```bash
mvnw.cmd test -Dtest=AuthServiceTest,PasswordResetServiceTest
```
