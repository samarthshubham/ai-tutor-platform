#!/usr/bin/env bash
# Times ping (cold and warm) and embedding of a 400-word text against a running tutor-api
# and prints a markdown table. Run it with the API on a real AI profile (ollama or cloud).
# Both endpoints are ADMIN-only: TOKEN is used when set, otherwise a token for admin1 is
# fetched from Keycloak through the tutor-cli client.
set -euo pipefail
shopt -s inherit_errexit

API_URL="${API_URL:-http://localhost:9190}"
WARM_RUNS="${WARM_RUNS:-3}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
BENCH_USER="${BENCH_USER:-admin1}"
BENCH_PASSWORD="${BENCH_PASSWORD:-Admin#2026}"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "bench-llm: $tool is required" >&2; exit 1; }
done

fetch_token() {
  local response token
  response=$(curl -sS "$KEYCLOAK_URL/realms/tutor/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=tutor-cli \
    --data-urlencode "username=$BENCH_USER" --data-urlencode "password=$BENCH_PASSWORD") || {
    echo "bench-llm: could not reach Keycloak on $KEYCLOAK_URL; set TOKEN or start the stack" >&2
    exit 1
  }
  token=$(jq -r '.access_token // empty' <<<"$response")
  if [[ -z "$token" ]]; then
    echo "bench-llm: Keycloak gave no token for $BENCH_USER: $response" >&2
    exit 1
  fi
  echo "$token"
}

if [[ -z "${TOKEN:-}" ]]; then
  TOKEN=$(fetch_token) || exit 1
fi

request() {
  local method="$1" path="$2" body="${3:-}"
  local response status
  if [[ -n "$body" ]]; then
    response=$(curl -sS -w '\n%{http_code} %{time_total}' -X "$method" -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' --data "$body" "$API_URL$path")
  else
    response=$(curl -sS -w '\n%{http_code} %{time_total}' -X "$method" -H "Authorization: Bearer $TOKEN" \
      "$API_URL$path")
  fi
  status=$(tail -n1 <<<"$response" | cut -d' ' -f1)
  if [[ "$status" != "200" ]]; then
    echo "bench-llm: $method $path returned $status: $(sed '$d' <<<"$response")" >&2
    exit 1
  fi
  printf '%s\n%s\n' "$(sed '$d' <<<"$response")" "$(tail -n1 <<<"$response" | cut -d' ' -f2)"
}

seconds_to_ms() { awk -v s="$1" 'BEGIN { printf "%d", s * 1000 }'; }

words=(lesson plants light sugar water roots leaves energy cells growth soil carbon oxygen sun green)
text=""
for i in $(seq 1 400); do
  text+="${words[$((i % ${#words[@]}))]} "
done
embed_body=$(jq -n --arg text "$text" '{text: $text}')

ping_once() {
  local out body
  out=$(request GET /api/ai/ping) || exit 1
  body=$(head -n1 <<<"$out")
  echo "$(jq -r '.provider' <<<"$body")|$(jq -r '.model' <<<"$body")|$(jq -r '.reply' <<<"$body" | tr '\n|' '  ' | cut -c1-40)|$(jq -r '.latencyMs' <<<"$body")|$(seconds_to_ms "$(tail -n1 <<<"$out")")"
}

embed_once() {
  local out body
  out=$(request POST /api/ai/embedding "$embed_body") || exit 1
  body=$(head -n1 <<<"$out")
  echo "$(jq -r '.provider' <<<"$body")|$(jq -r '.model' <<<"$body")|$(jq -r '.dimensions' <<<"$body") dims|$(jq -r '.latencyMs' <<<"$body")|$(seconds_to_ms "$(tail -n1 <<<"$out")")"
}

row() {
  local name="$1" line="$2"
  IFS='|' read -r provider model result model_ms total_ms <<<"$line"
  echo "| $name | $provider | $model | $result | $model_ms | $total_ms |"
}

echo "| Call | Provider | Model | Result | Model ms | Round trip ms |"
echo "|---|---|---|---|---|---|"
line=$(ping_once) || exit 1
row "ping (cold)" "$line"
for i in $(seq 1 "$WARM_RUNS"); do
  line=$(ping_once) || exit 1
  row "ping (warm $i)" "$line"
done
line=$(embed_once) || exit 1
row "embed 400 words (first)" "$line"
for i in $(seq 1 "$WARM_RUNS"); do
  line=$(embed_once) || exit 1
  row "embed 400 words (warm $i)" "$line"
done
