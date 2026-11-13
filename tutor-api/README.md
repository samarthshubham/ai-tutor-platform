# tutor-api

Spring Boot 4.1 backend of the AI tutor platform (Java 21, Maven wrapper). Listens on port `9190`.

## Prerequisites

- JDK 21 or newer
- Docker, for the integration tests (Testcontainers starts `pgvector/pgvector:pg16`)
- For local runs: PostgreSQL with pgvector on `localhost:5442`, database, user and password `tutor` (the compose database from `infra/`)

## Commands

```bash
./mvnw verify                                             # unit, integration and architecture tests, 90% line gate, Spotless check
./mvnw spotless:apply                                     # format the code
./mvnw test                                               # unit and architecture tests only, no Docker needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # run against localhost:5442/tutor
```

`TUTOR_DB_PASSWORD` overrides the dev database password.

## Profiles

| Profile | Purpose |
|---|---|
| `dev` | Datasource `localhost:5442/tutor`, Flyway `out-of-order` on |
| `test` | Used by integration tests; the database comes from the shared Testcontainers container in `support/IntegrationTest` |

## Endpoints

| Path | What |
|---|---|
| `/actuator/health` | Health (`UP` when the app and database are fine) |
| `/actuator/info` | Application info |
| `/actuator/prometheus` | Metrics for Prometheus |
| `/v3/api-docs` | OpenAPI document |
| `/swagger-ui/index.html` | Swagger UI |

## Tests

| Level | Naming | Runs in |
|---|---|---|
| unit, web slice, architecture | `*Test` | `./mvnw test` (Surefire) |
| integration | `*IT`, extends `support/IntegrationTest` | `./mvnw verify` (Failsafe) |

Error bodies, paging and the module rules are described in `docs/01-module-design.md` and `docs/02-api-design.md` §1.
