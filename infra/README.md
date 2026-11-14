# infra

> **Dev only.** Every password here (demo users in `keycloak/tutor-realm.json`, database, Keycloak and Grafana admins in `.env.example`) is a local development credential, documented in roadmap §5. Never reuse them anywhere else.

Local services the application needs, except the model (Ollama runs on the host on `11434`). tutor-api and tutor-web run on the host, not in containers. Ports are chosen so this stack runs next to `clinic-fhir-system`.

## Commands

Run from the repository root. Needs Docker; the smoke test also needs `curl` and `jq`.

```bash
docker compose -f infra/docker-compose.yml up -d --wait   # start; returns when every service is healthy
bash infra/smoke.sh                                       # check the running stack
bash infra/smoke.sh --lifecycle                           # also prove down keeps data and down -v wipes it (wipes local data)
docker compose -f infra/docker-compose.yml down           # stop, keep data
docker compose -f infra/docker-compose.yml down -v        # stop and wipe data; the realm is imported again on the next start
```

Copy `.env.example` to `infra/.env` to change the dev credentials; the compose file and the smoke test read it.

## Services

| Service | Image | Host port | Login |
|---|---|---|---|
| `postgres` | `pgvector/pgvector:0.8.7-pg16` | `5442` | database `tutor`, user `tutor` / `tutor`; database `keycloak`, user `keycloak` / `keycloak` |
| `keycloak` | `quay.io/keycloak/keycloak:26.8.2` | `8180` | admin console `http://localhost:8180/admin`, `admin` / `admin`; realm `tutor` |
| `mailpit` | `axllent/mailpit:v1.33.1` | SMTP `1125`, UI `8125` | `http://localhost:8125`, no login |
| `prometheus` | `prom/prometheus:v3.16.0` | `9191` | `http://localhost:9191`, no login; scrapes `host.docker.internal:9190/actuator/prometheus` |
| `grafana` | `grafana/grafana:13.3.1` | `3100` | `http://localhost:3100`, `admin` / `admin`; Prometheus datasource provisioned |

Every port is published on `127.0.0.1` only, so the dev credentials are not reachable from the network. Data lives in the named volumes `postgres-data`, `prometheus-data` and `grafana-data`. `postgres/create-keycloak-db.sh` creates the `keycloak` user and database the first time the volume is initialised.

## Realm `tutor`

Imported from `keycloak/tutor-realm.json` on start when the realm does not exist yet (`start-dev --import-realm`). After editing the file, run `down -v` and start again to re-import it.

- Realm roles `ADMIN`, `TEACHER`, `TUTOR`, `STUDENT`, `PARENT`; access tokens live 15 minutes.
- Client `tutor-web`: public, authorization code flow with PKCE `S256`, redirect `http://localhost:4300/*`, web origin `http://localhost:4300`.
- Client `tutor-cli`: public, direct access grants (password grant) only; for the seed, e2e and curl.

| Username | Name | Role | Password |
|---|---|---|---|
| `admin1` | Ada Admin | ADMIN | `Admin#2026` |
| `teacher1` | Tara Teacher | TEACHER | `Teacher#2026` |
| `teacher2` | Tom Teacher | TEACHER | `Teacher#2026` |
| `tutor1` | Uma Tutor | TUTOR | `Tutor#2026` |
| `student1` | Sam Student | STUDENT | `Student#2026` |
| `student2` | Sara Student | STUDENT | `Student#2026` |
| `student3` | Sid Student | STUDENT | `Student#2026` |
| `parent1` | Pat Parent | PARENT | `Parent#2026` |
| `parent2` | Priya Parent | PARENT | `Parent#2026` |

Every user's email is `<username>@tutor.local`.

## Get a token

```bash
TOKEN=$(curl -s http://localhost:8180/realms/tutor/protocol/openid-connect/token \
  -d grant_type=password -d client_id=tutor-cli \
  -d username=teacher1 --data-urlencode 'password=Teacher#2026' | jq -r .access_token)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:9190/api/me
```

`/api/me` exists from E1-S1. Decode the token's middle part to see `realm_access.roles`.

## Prometheus and tutor-api on WSL

Prometheus reaches tutor-api through `host.docker.internal`. With Docker Desktop on WSL that forward only works when the JVM listens on IPv4, so `./mvnw spring-boot:run` starts tutor-api with `-Djava.net.preferIPv4Stack=true` (set in `tutor-api/pom.xml`). A packaged jar needs `java -Djava.net.preferIPv4Stack=true -jar ...`. The target shows `down` in Prometheus while tutor-api is not running.
