#!/usr/bin/env bash
# Checks the running compose stack and exits non-zero on the first failure.
# Usage: bash infra/smoke.sh               checks the running stack
#        bash infra/smoke.sh --lifecycle   also proves `down` keeps data and `down -v` wipes it
#                                          (this wipes the local stack data)
# Needs docker, curl and jq.
set -euo pipefail

cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a
  . ./.env
  set +a
fi

COMPOSE=(docker compose -f docker-compose.yml)
SERVICES=(postgres keycloak mailpit prometheus grafana)
POSTGRES_IMAGE=pgvector/pgvector:0.8.7-pg16
KEYCLOAK=http://localhost:8180
GRAFANA_AUTH="${GRAFANA_ADMIN_USER:-admin}:${GRAFANA_ADMIN_PASSWORD:-admin}"
DEMO_USERS=(admin1:ADMIN teacher1:TEACHER teacher2:TEACHER tutor1:TUTOR
  student1:STUDENT student2:STUDENT student3:STUDENT parent1:PARENT parent2:PARENT)
ROLES='["ADMIN","TEACHER","TUTOR","STUDENT","PARENT"]'

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

ok() {
  echo "ok   $1"
}

psql_tutor() {
  docker run --rm --add-host host.docker.internal:host-gateway \
    -e PGPASSWORD="${TUTOR_DB_PASSWORD:-tutor}" -e PGOPTIONS="-c client_min_messages=warning" "$POSTGRES_IMAGE" \
    psql -h host.docker.internal -p 5442 -U tutor -d tutor -v ON_ERROR_STOP=1 -qtAc "$1"
}

title_case() {
  local lower=${1,,}
  echo "${lower^}"
}

access_token() {
  curl -fsS "$KEYCLOAK/realms/tutor/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=tutor-cli \
    --data-urlencode "username=$1" --data-urlencode "password=$2" | jq -er .access_token
}

jwt_claims() {
  jq -R 'split(".")[1] | gsub("-"; "+") | gsub("_"; "/") | . + ("=" * ((4 - length % 4) % 4)) | @base64d | fromjson' <<<"$1"
}

check_services_healthy() {
  local service state
  for service in "${SERVICES[@]}"; do
    state=$("${COMPOSE[@]}" ps --format '{{.Health}}' "$service" 2>/dev/null || true)
    [[ "$state" == healthy ]] || fail "service $service is '${state:-not running}', expected healthy"
  done
  ok "all services healthy: ${SERVICES[*]}"
}

check_postgres() {
  [[ $(psql_tutor 'select current_database()') == tutor ]] || fail "psql on 5442 did not connect to tutor"
  psql_tutor 'create extension if not exists vector' >/dev/null || fail "create extension vector failed on tutor"
  ok "psql on 5442 connects to tutor; extension vector available"
}

check_postgres_volume() {
  local mount
  mount=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Type}}{{end}}{{end}}' \
    "$("${COMPOSE[@]}" ps -q postgres)")
  [[ "$mount" == volume ]] || fail "postgres data is not on a named volume"
  ok "postgres data is on a named volume"
}

check_teacher_token() {
  local token
  token=$(access_token teacher1 'Teacher#2026') || fail "password grant for teacher1 on tutor-cli failed"
  jwt_claims "$token" | jq -e '.realm_access.roles | index("TEACHER")' >/dev/null ||
    fail "teacher1 token has no TEACHER in realm_access.roles"
  ok "teacher1 password grant on tutor-cli carries TEACHER"
}

check_demo_users() {
  local entry user role token claims
  for entry in "${DEMO_USERS[@]}"; do
    user=${entry%%:*}
    role=${entry#*:}
    token=$(access_token "$user" "$(title_case "$role")#2026") || fail "password grant for $user failed"
    claims=$(jwt_claims "$token")
    jq -e --argjson roles "$ROLES" --arg role "$role" \
      '[.realm_access.roles[] | select(IN($roles[]))] == [$role]' <<<"$claims" >/dev/null ||
      fail "$user does not have exactly the role $role"
    jq -e --arg email "$user@tutor.local" '.email == $email and (.given_name | length > 0) and (.family_name | length > 0)' \
      <<<"$claims" >/dev/null || fail "$user has the wrong email or no first and last name"
  done
  ok "all ${#DEMO_USERS[@]} demo users sign in with exactly one role, email and name"
}

check_mailpit() {
  curl -fsS http://localhost:8125/api/v1/info >/dev/null || fail "Mailpit UI does not answer on 8125"
  exec 3<>/dev/tcp/localhost/1125 || fail "Mailpit SMTP does not answer on 1125"
  local banner
  read -r -t 5 banner <&3 || true
  exec 3<&-
  [[ "$banner" == 220* ]] || fail "Mailpit SMTP on 1125 sent no 220 greeting"
  ok "Mailpit UI on 8125, SMTP on 1125"
}

check_prometheus() {
  curl -fsS http://localhost:9191/-/healthy >/dev/null || fail "Prometheus does not answer on 9191"
  curl -fsS http://localhost:9191/api/v1/targets |
    jq -e '.data.activeTargets[] | select(.scrapeUrl == "http://host.docker.internal:9190/actuator/prometheus")' >/dev/null ||
    fail "Prometheus does not scrape host.docker.internal:9190/actuator/prometheus"
  ok "Prometheus on 9191 scrapes tutor-api on host.docker.internal:9190"
}

check_grafana() {
  curl -fsS http://localhost:3100/api/health >/dev/null || fail "Grafana does not answer on 3100"
  curl -fsS -u "$GRAFANA_AUTH" http://localhost:3100/api/datasources/uid/prometheus |
    jq -e '.type == "prometheus"' >/dev/null || fail "Grafana has no Prometheus datasource"
  curl -fsS -u "$GRAFANA_AUTH" http://localhost:3100/api/datasources/uid/prometheus/health |
    jq -e '.status == "OK"' >/dev/null || fail "Grafana cannot reach Prometheus"
  ok "Grafana on 3100 with a working Prometheus datasource"
}

check_lifecycle() {
  echo "lifecycle: this restarts the stack and wipes its volumes"
  psql_tutor 'create table if not exists smoke_marker (id int)' >/dev/null
  "${COMPOSE[@]}" down
  "${COMPOSE[@]}" up -d --wait
  [[ $(psql_tutor "select to_regclass('smoke_marker') is not null") == t ]] || fail "data was lost after down"
  ok "down keeps data"
  "${COMPOSE[@]}" down -v
  "${COMPOSE[@]}" up -d --wait
  [[ $(psql_tutor "select to_regclass('smoke_marker') is null") == t ]] || fail "data survived down -v"
  ok "down -v removes data"
}

check_all() {
  check_services_healthy
  check_postgres
  check_postgres_volume
  check_teacher_token
  check_demo_users
  check_mailpit
  check_prometheus
  check_grafana
}

check_all
if [[ "${1:-}" == --lifecycle ]]; then
  check_lifecycle
  check_all
fi
echo "Smoke test passed."
