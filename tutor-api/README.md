# tutor-api

Spring Boot 4.1 backend of the AI tutor platform (Java 21, Maven wrapper). Listens on port `9190`.

## Prerequisites

- JDK 21 or newer
- Docker, for the integration tests (Testcontainers starts `pgvector/pgvector:pg16`)
- For local runs: PostgreSQL with pgvector on `localhost:5442`, database, user and password `tutor` (the compose database from `infra/`)
- For the `ollama` profile: Ollama on `localhost:11434` with `llama3.2:3b` and `nomic-embed-text` pulled
- For the `cloud` profile: `ANTHROPIC_API_KEY` set, and Ollama for the embedding model

## Commands

```bash
./mvnw verify                                             # unit, integration and architecture tests, 90% line gate, Spotless check
./mvnw spotless:apply                                     # format the code
./mvnw test                                               # unit and architecture tests only, no Docker needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,fake-ai   # run against localhost:5442/tutor, no model needed
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ollama    # or dev,cloud
bash ../scripts/bench-llm.sh                                    # with the API on ollama or cloud: times ping and a 400-word embedding
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

## Endpoints

| Path | What |
|---|---|
| `GET /api/ai/ping` | One tiny model call: `{"provider","model","reply","latencyMs"}`; `503 AI_UNAVAILABLE` on timeout |
| `POST /api/ai/embedding` | Embeds `{"text"}` and returns the vector size: `{"provider","model","dimensions","latencyMs"}` |
| `/actuator/health` | Health (`UP` when the app and database are fine) |
| `/actuator/info` | Application info; `ai.chat-provider` names the active chat provider (`fake`, `ollama` or `anthropic`) |
| `/actuator/prometheus` | Metrics for Prometheus |
| `/v3/api-docs` | OpenAPI document |
| `/swagger-ui/index.html` | Swagger UI |

## Tests

| Level | Naming | Runs in |
|---|---|---|
| unit, web slice, architecture | `*Test` | `./mvnw test` (Surefire) |
| integration | `*IT`, extends `support/IntegrationTest` | `./mvnw verify` (Failsafe) |

Error bodies, paging and the module rules are described in `docs/01-module-design.md` and `docs/02-api-design.md` §1.
