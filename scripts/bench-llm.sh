#!/usr/bin/env bash
# Times ping (cold and warm) and embedding of a 400-word text against a running tutor-api
# and prints a markdown table. Run it with the API on a real AI profile (ollama or cloud).
set -euo pipefail

API_URL="${API_URL:-http://localhost:9190}"
WARM_RUNS="${WARM_RUNS:-3}"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "bench-llm: $tool is required" >&2; exit 1; }
done

request() {
  local method="$1" path="$2" body="${3:-}"
  local response status
  if [[ -n "$body" ]]; then
    response=$(curl -sS -w '\n%{http_code} %{time_total}' -X "$method" -H 'Content-Type: application/json' \
      --data "$body" "$API_URL$path")
  else
    response=$(curl -sS -w '\n%{http_code} %{time_total}' -X "$method" "$API_URL$path")
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
  out=$(request GET /api/ai/ping)
  body=$(head -n1 <<<"$out")
  echo "$(jq -r '.provider' <<<"$body")|$(jq -r '.model' <<<"$body")|$(jq -r '.reply' <<<"$body" | tr '\n|' '  ' | cut -c1-40)|$(jq -r '.latencyMs' <<<"$body")|$(seconds_to_ms "$(tail -n1 <<<"$out")")"
}

embed_once() {
  local out body
  out=$(request POST /api/ai/embedding "$embed_body")
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
row "ping (cold)" "$(ping_once)"
for i in $(seq 1 "$WARM_RUNS"); do
  row "ping (warm $i)" "$(ping_once)"
done
row "embed 400 words (first)" "$(embed_once)"
for i in $(seq 1 "$WARM_RUNS"); do
  row "embed 400 words (warm $i)" "$(embed_once)"
done
