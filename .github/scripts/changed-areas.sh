#!/usr/bin/env bash
# Writes backend= and frontend=true|false to $GITHUB_OUTPUT (or stdout),
# so CI skips test jobs whose code a pull request does not touch.
# Pushes to main always run everything. Changing ci.yml runs everything.
#
# Env: GITHUB_EVENT_NAME, BASE_REF. DIFF_RANGE overrides the compared range (local testing).
set -euo pipefail

output() {
  echo "$1=$2" >>"${GITHUB_OUTPUT:-/dev/stdout}"
}

exists() {
  [[ -f "$1" ]] && echo true || echo false
}

if [[ "${GITHUB_EVENT_NAME:-}" != "pull_request" && -z "${DIFF_RANGE:-}" ]]; then
  output backend "$(exists tutor-api/pom.xml)"
  output frontend "$(exists tutor-web/package.json)"
  exit 0
fi

files=$(git diff --name-only "${DIFF_RANGE:-origin/${BASE_REF:?}...HEAD}")

touches() {
  local include=$1 exclude=$2
  grep -qxF '.github/workflows/ci.yml' <<<"$files" && return 0
  grep -E "$include" <<<"$files" | grep -qvE "$exclude"
}

if [[ -f tutor-api/pom.xml ]] && touches '^tutor-api/' '\.md$'; then
  output backend true
else
  output backend false
fi

if [[ -f tutor-web/package.json ]] && touches '^tutor-web/' '\.md$'; then
  output frontend true
else
  output frontend false
fi
