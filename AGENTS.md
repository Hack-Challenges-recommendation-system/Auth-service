# auth-service

Registration, login and JWT issuing. It came from the team's earlier project; roles, bans and email verification were removed because this project doesn't need them.

## Project

Recommendation System is an entry for Hack&Change 2026, Alfa-Bank case «Альфа-Курс: знания в нужный момент», track 1: personal recommendations of educational materials for entrepreneurs. A vendor LLM ranks and explains materials; the services around it supply context, check its answers and record what users do.

| Repository | Role |
|---|---|
| `api-gateway` | Single entry point. Checks access tokens, routes `/api/v1/**`, sets `X-User-Id`. |
| `auth-service` | Registration, login, access and refresh JWT, logout denylist. |
| `profile-service` | Business profile of the user (onboarding answers). |
| `catalog-service` | Catalog of materials from the case data, with Russian full-text search. |
| `metric-service` | Raw user events and the metrics computed from them. |
| `recommendation-service` | Builds recommendations with the LLM. |
| `frontend` | Angular web app. |
| `Documentation` | Architecture docs. Only developers commit there; agents never do. |

All repositories live in the GitHub organization `Hack-Challenges-recommendation-system` and are **public**.

## Rules for every change

These come from the case. Breaking one can disqualify the submission.

1. **The case data never goes into git or a Docker image**: not the dump, not a copy, not an extract. Services read it from a path mounted at runtime; `/data/` is in `.gitignore`. Synthetic test data written by the team may be committed.
2. **No keys, tokens or passwords in git.** Read them from environment variables; `env.example` files list names only.
3. **External APIs, such as the LLM, get only the fragments they need.** No user ids, names, emails or other personal data, and no paid or closed materials.
4. **Paid materials:** show only a short benefit and the provided link, never their content.
5. **Everything must start with one `docker compose up` from the README.** A submission that doesn't start scores 0. Don't add infrastructure or a required external service without agreeing on it first.
6. **Simple UI, no intrusive notifications.**

## Commits

Every commit follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), in the style the history already uses:

    <type>(<scope>): <what was changed>

- **Type:** `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style` or `revert`.
- **Scope:** optional; the area that changed, e.g. `(jwt)`, `(kafka)`, `(migrations)`, `(controllers)`, `(CI/CD)`.
- **Description:** English, past tense, e.g. `fix(cookie): logout cookie name was fixed`.
- **Breaking change:** `!` after the type or scope, plus a `BREAKING CHANGE:` footer saying what callers must change.
- One logical change per commit. Commit to `develop`; `main` receives merges from `develop`.

## Code conventions

- Java 25, Spring Boot 4.1.2, Maven. Base package `team.nofantasy.recommendationsystem.<service>`, entry point `Main`.
- Three layers: `controller` → `service` → `repository`. Controllers never touch repositories or entities.
- Two DTO layers, both Java records:
  - `dto/controller/<feature>/*ControllerDto`: HTTP requests and responses, with Jakarta validation.
  - `dto/service/<feature>/*ServiceDto`: between controllers and services. Kafka messages live in `dto/service/kafka`, import rows in `dto/service/importer`.
- Mapping goes through MapStruct interfaces in `mapper/`. Unmapped source or target properties fail the build, so map every field or ignore it explicitly. Keep methods in pairs: controller-to-service first, then service-to-controller.
- Entities in `entity/` use Lombok: `@Getter`, `@Setter`, `@Builder` and a protected no-args constructor. Enums of an entity are nested in it.
- Domain exceptions live in `exception/`; `controller/advice/GlobalExceptionHandler` turns them into HTTP statuses.
- Schema changes go only into new Flyway files in `src/main/resources/database/migrations` (`V<n>__<description>.sql`). Never edit a migration that a shared database already ran. Hibernate runs with `ddl-auto: validate`.
- Endpoints sit under `/api/v1` (`spring.mvc.servlet.path`). Each service owns distinct top-level paths, so the gateway can route by prefix.
- The gateway puts the signed-in user's id into `X-User-Id`. Read it with `@RequestHeader("X-User-Id") UUID userId`, never from the body.
- Log with the SLF4J fluent API: `log.atInfo().setMessage(...).addKeyValue(...).log()`. Files go to `/logs/<service>.log` as ECS JSON.
- Configuration comes from environment variables, with local defaults in `application.yaml`.

## Build and test

- `mvn -B -ntp verify` runs all tests. Integration tests use Testcontainers, so Docker must be running; without Docker they are skipped.
- `mvn -B -ntp -DskipTests package` builds `target/app.jar`, which the `Dockerfile` copies.
- `docker compose up -d` starts the local infrastructure of this service (`docker-compose.yaml`).
- In MockMvc tests, request the full path and set the servlet path: `get("/api/v1/...").servletPath("/api/v1")`.
- CI: `.github/workflows/ci-cd.yml` tests every push and pull request, pushes the image to Docker Hub from `main`, and deploys on a manual run. Its header lists the secrets and variables it needs.

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
| `GET /session`, `DELETE /session`, `DELETE /session/all` | signed in | Lists or ends device sessions |
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
