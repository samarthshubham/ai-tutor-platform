#!/usr/bin/env bash
# Runs the whole local stack: the compose services, tutor-api and tutor-app.
# Usage: ./dev.sh [start|stop|status|logs <service>]   (no argument means start)
# Env:   AI_PROFILE=ollama|cloud|fake-ai  skips the Ollama check
#        OLLAMA_URL                       Ollama to check and use (default http://localhost:11434)
# Never run `./dev.sh start` under `timeout`: its signal trap tears the stack down.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/logs"
PID_FILE="$ROOT/.dev.pids"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")
COMPOSE_SERVICES=(postgres keycloak mailpit prometheus grafana)
APP_SERVICES=(tutor-api tutor-app)
AI_PROFILES=(ollama cloud fake-ai)
API_URL=http://localhost:9190
WEB_URL=http://localhost:4300
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
API_WAIT_SECONDS="${API_WAIT_SECONDS:-300}"
WEB_WAIT_SECONDS="${WEB_WAIT_SECONDS:-300}"
STOP_WAIT_SECONDS=20

if [[ -t 1 ]]; then
  GREEN=$'\033[0;32m' YELLOW=$'\033[1;33m' RED=$'\033[0;31m' CYAN=$'\033[0;36m' BOLD=$'\033[1m' NC=$'\033[0m'
else
  GREEN='' YELLOW='' RED='' CYAN='' BOLD='' NC=''
fi

ok() { echo "  ${GREEN}ok${NC}    $1"; }
note() { echo "  ${YELLOW}..${NC}    $1"; }
err() { echo "  ${RED}fail${NC}  $1" >&2; }
section() { echo; echo "${BOLD}${CYAN}> $1${NC}"; }

if [[ -f "$ROOT/infra/.env" ]]; then
  set -a
  . "$ROOT/infra/.env"
  set +a
fi

require_commands() {
  local command
  for command in docker curl ss setsid java node npm; do
    command -v "$command" >/dev/null || { err "$command is not installed or not on PATH"; exit 1; }
  done
  docker info >/dev/null 2>&1 || { err "the Docker daemon is not running"; exit 1; }
}

recorded() {
  if [[ -f "$PID_FILE" ]]; then
    awk -v key="$1" '$1 == key { print $2 }' "$PID_FILE"
  fi
}

group_alive() {
  [[ -n "$1" ]] && kill -0 -- "-$1" 2>/dev/null
}

port_in_use() {
  ss -ltnH "sport = :$1" | grep -q .
}

compose_running() {
  [[ -n "$("${COMPOSE[@]}" ps --quiet 2>/dev/null)" ]]
}

api_healthy() {
  curl -fsS --max-time 2 "$API_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'
}

web_serving() {
  curl -fsS --max-time 2 "$WEB_URL/" 2>/dev/null | grep -q '<tp-root'
}

ollama_answers() {
  curl -fsS --max-time 2 "$OLLAMA_URL/api/tags" >/dev/null 2>&1
}

stop_group() {
  local name=$1 pid=$2 waited=0
  group_alive "$pid" || return 0
  kill -TERM -- "-$pid" 2>/dev/null || true
  while group_alive "$pid" && ((waited < STOP_WAIT_SECONDS)); do
    sleep 1
    waited=$((waited + 1))
  done
  if group_alive "$pid"; then
    kill -KILL -- "-$pid" 2>/dev/null || true
    note "$name did not stop in ${STOP_WAIT_SECONDS}s and was killed"
  fi
  ok "$name stopped"
}

teardown() {
  section "Stopping the stack"
  local app_pid api_pid
  app_pid=$(recorded tutor-app)
  api_pid=$(recorded tutor-api)
  rm -f "$PID_FILE"
  stop_group tutor-app "$app_pid"
  stop_group tutor-api "$api_pid"
  if "${COMPOSE[@]}" down >>"$LOG_DIR/docker.log" 2>&1; then
    ok "containers stopped (data kept)"
  else
    err "docker compose down failed, see logs/docker.log"
  fi
}

die() {
  err "$1"
  teardown
  exit 1
}

on_signal() {
  echo
  teardown
  exit 0
}

stop_requested() {
  [[ ! -f "$PID_FILE" ]]
}

show_log_tail() {
  echo "  last lines of logs/$1.log:" >&2
  tail -n 20 "$LOG_DIR/$1.log" | sed 's/^/    /' >&2
}

wait_until() {
  local name=$1 seconds=$2 check=$3 pid waited=0
  pid=$(recorded "$name")
  note "waiting for $name (up to ${seconds}s)"
  until "$check"; do
    stop_requested && exit 0
    if ! group_alive "$pid"; then
      show_log_tail "$name"
      die "$name exited during startup"
    fi
    if ((waited >= seconds)); then
      show_log_tail "$name"
      die "$name did not become ready in ${seconds}s"
    fi
    sleep 2
    waited=$((waited + 2))
  done
}

start_in_own_group() {
  local name=$1 dir=$2
  shift 2
  setsid bash -c 'cd "$1" && shift && exec "$@"' _ "$dir" "$@" >"$LOG_DIR/$name.log" 2>&1 </dev/null &
  echo "$name $!" >>"$PID_FILE"
}

choose_ai_profile() {
  if [[ -n "${AI_PROFILE:-}" ]]; then
    [[ " ${AI_PROFILES[*]} " == *" $AI_PROFILE "* ]] ||
      die "AI_PROFILE must be one of: ${AI_PROFILES[*]} (got '$AI_PROFILE')"
    chosen_profile=$AI_PROFILE
    ok "AI profile ${BOLD}$chosen_profile${NC} (set by AI_PROFILE)"
  elif ollama_answers; then
    chosen_profile=ollama
    ok "AI profile ${BOLD}ollama${NC} (Ollama answers on $OLLAMA_URL)"
  else
    chosen_profile=fake-ai
    ok "AI profile ${BOLD}fake-ai${NC} (Ollama does not answer on $OLLAMA_URL; using the deterministic fake model)"
  fi
  if [[ "$chosen_profile" == cloud && -z "${ANTHROPIC_API_KEY:-}" ]]; then
    die "AI_PROFILE=cloud needs ANTHROPIC_API_KEY"
  fi
}

print_summary() {
  echo
  echo "${BOLD}The stack is running.${NC} AI profile: ${BOLD}$chosen_profile${NC}"
  echo "  Web app      $WEB_URL"
  echo "  API          $API_URL   (Swagger UI $API_URL/swagger-ui/index.html)"
  echo "  Keycloak     http://localhost:8180/admin   ${KEYCLOAK_ADMIN_USER:-admin} / ${KEYCLOAK_ADMIN_PASSWORD:-admin}"
  echo "  Mailpit      http://localhost:8125"
  echo "  Prometheus   http://localhost:9191"
  echo "  Grafana      http://localhost:3100   ${GRAFANA_ADMIN_USER:-admin} / ${GRAFANA_ADMIN_PASSWORD:-admin}"
  echo "  Postgres     localhost:5442, database tutor, user tutor"
  echo
  echo "Demo logins (realm tutor):"
  echo "  admin1                          Admin#2026"
  echo "  teacher1, teacher2              Teacher#2026"
  echo "  tutor1                          Tutor#2026"
  echo "  student1, student2, student3    Student#2026"
  echo "  parent1, parent2                Parent#2026"
  echo
  echo "Logs are in logs/; ./dev.sh logs <service> shows one. ./dev.sh status lists every service."
  echo "Press Ctrl+C to stop everything, or run ./dev.sh stop from another terminal."
}

start() {
  require_commands
  local name pid
  for name in "${APP_SERVICES[@]}"; do
    pid=$(recorded "$name")
    if group_alive "$pid"; then
      echo "${YELLOW}The stack is already running.${NC} Use ./dev.sh status, or ./dev.sh stop first."
      exit 1
    fi
  done
  port_in_use 9190 && { err "port 9190 is taken by a process dev.sh did not start; stop it first"; exit 1; }
  port_in_use 4300 && { err "port 4300 is taken by a process dev.sh did not start; stop it first"; exit 1; }

  mkdir -p "$LOG_DIR"
  : >"$PID_FILE"
  : >"$LOG_DIR/docker.log"
  trap on_signal INT TERM HUP

  section "Infrastructure (Postgres, Keycloak, Mailpit, Prometheus, Grafana)"
  note "starting containers and waiting until they are healthy"
  "${COMPOSE[@]}" up -d --wait >>"$LOG_DIR/docker.log" 2>&1 ||
    die "the containers did not become healthy, see logs/docker.log and ./dev.sh logs <service>"
  ok "containers healthy"

  section "tutor-api on :9190"
  choose_ai_profile
  echo "ai-profile $chosen_profile" >>"$PID_FILE"
  SPRING_OUTPUT_ANSI_ENABLED=never SPRING_AI_OLLAMA_BASE_URL="$OLLAMA_URL" \
    start_in_own_group tutor-api "$ROOT/tutor-api" \
    ./mvnw -B spring-boot:run "-Dspring-boot.run.profiles=dev,$chosen_profile"
  wait_until tutor-api "$API_WAIT_SECONDS" api_healthy
  ok "tutor-api healthy on $API_URL (logs/tutor-api.log)"

  section "tutor-app on :4300"
  if [[ ! -d "$ROOT/tutor-web/node_modules" ]]; then
    note "installing frontend dependencies (npm ci, logs/npm-ci.log)"
    (cd "$ROOT/tutor-web" && npm ci --no-audit --no-fund >"$LOG_DIR/npm-ci.log" 2>&1) ||
      die "npm ci failed, see logs/npm-ci.log"
  fi
  NX_DAEMON=false NX_NO_CLOUD=true NO_COLOR=1 \
    start_in_own_group tutor-app "$ROOT/tutor-web" npx nx serve tutor-app
  wait_until tutor-app "$WEB_WAIT_SECONDS" web_serving
  ok "tutor-app serving $WEB_URL (logs/tutor-app.log)"

  print_summary
  until stop_requested; do
    sleep 2
  done
  echo "Stopped by ./dev.sh stop."
}

stop() {
  if [[ -f "$PID_FILE" ]] || compose_running; then
    mkdir -p "$LOG_DIR"
    teardown
  else
    echo "Nothing is running."
  fi
}

print_status() {
  printf '  %-11s %s\n' "$1" "$2"
}

status() {
  local service state health pid profile
  echo "${BOLD}Containers${NC}"
  for service in "${COMPOSE_SERVICES[@]}"; do
    state=$("${COMPOSE[@]}" ps --format '{{.State}}' "$service" 2>/dev/null || true)
    health=$("${COMPOSE[@]}" ps --format '{{.Health}}' "$service" 2>/dev/null || true)
    print_status "$service" "${state:-stopped}${health:+ ($health)}"
  done
  echo "${BOLD}Applications${NC}"
  profile=$(recorded ai-profile)
  pid=$(recorded tutor-api)
  if api_healthy; then
    print_status tutor-api "running, healthy on $API_URL${profile:+ (AI profile $profile)}"
  elif group_alive "$pid"; then
    print_status tutor-api "starting (PID $pid)"
  else
    print_status tutor-api stopped
  fi
  pid=$(recorded tutor-app)
  if web_serving; then
    print_status tutor-app "running on $WEB_URL"
  elif group_alive "$pid"; then
    print_status tutor-app "starting (PID $pid)"
  else
    print_status tutor-app stopped
  fi
  echo "${BOLD}Model${NC}"
  if ollama_answers; then
    print_status ollama "answers on $OLLAMA_URL"
  else
    print_status ollama "does not answer on $OLLAMA_URL"
  fi
}

logs() {
  local service=${1:-} follow=()
  [[ -t 1 ]] && follow=(-f)
  if [[ " ${APP_SERVICES[*]} " == *" $service "* ]]; then
    [[ -f "$LOG_DIR/$service.log" ]] || { err "no logs/$service.log yet"; exit 1; }
    exec tail -n 200 "${follow[@]}" "$LOG_DIR/$service.log"
  elif [[ " ${COMPOSE_SERVICES[*]} " == *" $service "* ]]; then
    exec "${COMPOSE[@]}" logs --tail 200 "${follow[@]}" "$service"
  else
    err "unknown service '${service}'; one of: ${APP_SERVICES[*]} ${COMPOSE_SERVICES[*]}"
    exit 1
  fi
}

usage() {
  echo "Usage: ./dev.sh [start|stop|status|logs <service>]"
  echo "  start            start everything and wait; Ctrl+C stops it (default)"
  echo "  stop             stop everything dev.sh started and the containers (data is kept)"
  echo "  status           show each service"
  echo "  logs <service>   last 200 lines, followed in a terminal; services: ${APP_SERVICES[*]} ${COMPOSE_SERVICES[*]}"
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  status) status ;;
  logs) logs "${2:-}" ;;
  -h | --help | help) usage ;;
  *)
    usage >&2
    exit 1
    ;;
esac
