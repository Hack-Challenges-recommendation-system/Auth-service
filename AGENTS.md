# auth-service

Registration, login and JWT issuing. It came from the team's earlier project; roles, bans and email verification were removed because this project doesn't need them.

**Read the shared guide first:** [AGENTS.md in Documentation](https://github.com/Hack-Challenges-recommendation-system/Documentation/blob/develop/AGENTS.md) covers the project, its architecture, the rules from the case (the case data and keys never go into git; this repository is public), the commit conventions and the conventions of the Java services. Locally it is `AGENTS.md` in your clone of the Documentation repository. This file adds only what is specific to auth-service.

## How auth works

- **Access token:** HS512 JWT, 5 minutes by default. Claims: `sub` (user id), `type` = `ACCESS`, `jti`.
- **Refresh token:** HS512 JWT with `type` = `REFRESH`, 30 days by default. Only its SHA-512 hash is stored, in `jwt_refresh_token`, one row per logged-in device (a "session"). The client gets it as the `jwtRefreshToken` HttpOnly cookie.
- **Login** sets the cookie and answers 204. The access token comes from `POST /auth/refresh` as plain text.
- **Logout** deletes the refresh token of the device and denylists the access token in Redis under `jwt:denylist:<jti>` until it would expire. The gateway must reject denylisted tokens.
- **Registration** publishes `users.to.create` `{userId, nickname}`; deleting the account publishes `users.to.delete` `{userId}`. The key is the user id and there are no type headers. profile-service consumes both.
- `JWT_SECRET` must be at least 64 UTF-8 bytes, or the service refuses to start. The gateway verifies tokens with the same secret.

## Endpoints (under `/api/v1`)

| Method and path | Caller | What it does |
|---|---|---|
| `POST /user` | anyone | Registers `{nickname, email, password}` → 201; 409 if the email is taken |
| `POST /auth/login` | anyone | `{email, password}` → 204 and the cookie; 401 on wrong credentials |
| `POST /auth/refresh` | cookie | → 200 with a new access token |
| `DELETE /auth/logout` | cookie, optional bearer token | → 204, cookie cleared; works even when the tokens are already invalid |
| `PUT /user/password` | signed in | `{oldPassword, newPassword}`; 403 if the old one is wrong |
| `PUT /user/email` | signed in | `{email}`; 409 if taken |
| `DELETE /user` | signed in | Deletes the account and its sessions → 204 |
| `GET /health` | anyone | 200 |

"Signed in" means the gateway checked the access token and set `X-User-Id`.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:1234/postgres` | |
| `DB_USERNAME`, `DB_PASSWORD` | `admin`, `admin` | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, `secret` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `JWT_SECRET` | none, required | At least 64 bytes; the same value goes to the gateway |
| `JWT_ACCESS_TOKEN_TTL` | `5m` | |
| `JWT_REFRESH_TOKEN_TTL` | `30d` | |

`docker compose up -d` starts Postgres on `127.0.0.1:1234`, Redis on 6379 and Kafka on 9092.

## Tests

- Unit: `JwtServiceTest`, `AuthenticationServiceTest`, `UserCredentialsServiceTest`.
- Web layer: `AuthenticationControllerTest`, `UserCredentialsControllerTest`.
- `AuthFlowIntegrationTest`: register → login → refresh → logout → denylist, password change, account deletion and the Kafka events, on real Postgres, Redis and Kafka.

## Known gaps

- Refresh tokens aren't rotated on refresh (the `TODO` in `AuthenticationController`).
- The refresh cookie has `Secure` off for local HTTP; turn it on behind HTTPS.
- Error bodies are empty or ad hoc; RFC 9457 problem details are a `TODO` in `GlobalExceptionHandler`.
