# tutor-api

Spring Boot 4.1 backend of the AI tutor platform (Java 21, Maven wrapper). Listens on port `9190`.

## Prerequisites

- JDK 21 or newer
- Docker, for the integration tests (Testcontainers starts `pgvector/pgvector:pg16`)
- For local runs: PostgreSQL with pgvector on `localhost:5442`, database, user and password `tutor` (the compose database from `infra/`), and Keycloak on `localhost:8180` (realm `tutor`) to issue tokens
- For the `ollama` profile: Ollama on `localhost:11434` with `llama3.2:3b` and `nomic-embed-text` pulled
- For the `cloud` profile: `ANTHROPIC_API_KEY` set, and Ollama for the embedding model

## Commands

```bash
./mvnw verify                                             # unit, integration and architecture tests, 90% line gate, Spotless check
./mvnw spotless:apply                                     # format the code
./mvnw test                                               # unit and architecture tests only, no Docker needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,fake-ai   # run against localhost:5442/tutor, no model needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ollama    # or dev,cloud
bash ../scripts/bench-llm.sh                                    # with the API on ollama or cloud: times ping and a 400-word embedding as admin1 (or TOKEN)
```

Exactly one AI profile (`ollama`, `cloud`, `fake-ai`) must be active; the app refuses to start with none or more than one, and `cloud` refuses to start without `ANTHROPIC_API_KEY`.

`TUTOR_DB_PASSWORD` overrides the dev database password.

## Profiles

| Profile | Purpose |
|---|---|
| `dev` | Datasource `localhost:5442/tutor`, Flyway `out-of-order` on |
| `test` | Used by integration tests together with `fake-ai`; the database comes from the shared Testcontainers container in `support/IntegrationTest` |
| `ollama` | Chat `llama3.2:3b` (`tutor.ai.ollama.chat-model`) and embeddings `nomic-embed-text`, both on local Ollama |
| `cloud` | Chat on Anthropic (`tutor.ai.cloud.chat-model`, key from `ANTHROPIC_API_KEY`), embeddings on local Ollama |
| `fake-ai` | `FakeChatModel` and `FakeEmbeddingModel`; deterministic, used by tests, CI and e2e |

## Security

tutor-api is a stateless OAuth2 resource server for the Keycloak realm `tutor` (`tutor.security.issuer-uri`, default `http://localhost:8180/realms/tutor`). Every request needs `Authorization: Bearer <access token>` except `/actuator/health/**`, `/actuator/prometheus`, `/v3/api-docs/**` and `/swagger-ui/**`. The token's `realm_access.roles` must hold exactly one of `ADMIN`, `TEACHER`, `TUTOR`, `STUDENT`, `PARENT`; otherwise the answer is `403`. CORS allows `http://localhost:4300` (`tutor.security.allowed-origins`). The first request that needs the current user creates its `app_user` row.

```bash
TOKEN=$(curl -s http://localhost:8180/realms/tutor/protocol/openid-connect/token \
  -d grant_type=password -d client_id=tutor-cli \
  -d username=teacher1 --data-urlencode 'password=Teacher#2026' | jq -r .access_token)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:9190/api/me
```

Swagger UI has an "Authorize" button that takes the token. Tests never call Keycloak: `support/TestJwt` builds tokens for MockMvc, and integration tests use a fixed-key `JwtDecoder`.

## Endpoints

| Path | Roles | What |
|---|---|---|
| `GET /api/me` | all | The current user: `{"id","username","displayName","email","role","schoolId"}` |
| `GET /api/ai/ping` | ADMIN | One tiny model call: `{"provider","model","reply","latencyMs"}`; `503 AI_UNAVAILABLE` on timeout |
| `POST /api/ai/embedding` | ADMIN | Embeds `{"text"}` and returns the vector size: `{"provider","model","dimensions","latencyMs"}` |
| `/actuator/health` | open | Health (`UP` when the app and database are fine) |
| `/actuator/info` | all | Application info; `ai.chat-provider` names the active chat provider (`fake`, `ollama` or `anthropic`) |
| `/actuator/prometheus` | open | Metrics for Prometheus |
| `/v3/api-docs` | open | OpenAPI document |
| `/swagger-ui/index.html` | open | Swagger UI |

`401` (`UNAUTHENTICATED`) and `403` (`FORBIDDEN`) use the shared `ErrorResponse` body.

## Tests

| Level | Naming | Runs in |
|---|---|---|
| unit, web slice, architecture | `*Test` | `./mvnw test` (Surefire) |
| integration | `*IT`, extends `support/IntegrationTest` | `./mvnw verify` (Failsafe) |

Error bodies, paging and the module rules are described in `docs/01-module-design.md` and `docs/02-api-design.md` §1.
