#!/usr/bin/env bash
# Cheap, deterministic checks for pull requests that change code, run before the
# roadmap-review agent. Writes review=true|false and story=<id> to $GITHUB_OUTPUT
# (or stdout) and exits 1 when a code PR breaks a rule.
# Passing it proves nothing about the work: a pasted story line and any test file
# satisfy it. The roadmap-review agent verifies the claims against the diff.
#
# Env: BASE_REF, or DIFF_RANGE to override the compared range (local testing).
#      PR_BODY, or PR_BODY_FILE pointing to a file holding it.
set -euo pipefail

ROADMAP=docs/roadmap/ai-tutor.md
CODE_PATHS='^tutor-api/|^tutor-web/|^infra/|^scripts/|^dev\.sh$'

output() {
  echo "$1=$2" >>"${GITHUB_OUTPUT:-/dev/stdout}"
}

fail() {
  echo "::error::$1" >&2
  failed=true
}

files=$(git diff --name-only "${DIFF_RANGE:-origin/${BASE_REF:?}...HEAD}")
body=${PR_BODY:-}
if [[ -n "${PR_BODY_FILE:-}" ]]; then
  body=$(cat "$PR_BODY_FILE")
fi

if ! grep -qE "$CODE_PATHS" <<<"$files"; then
  output review false
  output story ""
  echo "No code paths changed; roadmap checks skipped."
  exit 0
fi

failed=false

story_line=$(grep -m 1 -E 'Roadmap story:' <<<"$body" || true)
stories=$(grep -oE 'E[0-9]+-S[0-9]+' <<<"$story_line" || true)
story=$(tr '\n' ',' <<<"$stories" | sed 's/,$//')
output story "$story"

if [[ -z "$story" ]]; then
  if grep -qiE 'Roadmap story:\s*none\b' <<<"$story_line"; then
    output review false
    echo "Roadmap story: none. Work outside the roadmap; roadmap review skipped."
    exit 0
  fi
  output review true
  fail "Code paths changed but the PR description has no 'Roadmap story: E<n>-S<n>' line (or 'Roadmap story: none' for work outside the roadmap)."
  exit 1
fi
output review true

for id in $stories; do
  grep -qE "^#### $id " "$ROADMAP" || fail "Roadmap story $id is not in $ROADMAP."
done

if grep -qE '^tutor-api/src/main/' <<<"$files" && ! grep -qE '^tutor-api/src/test/' <<<"$files"; then
  fail "tutor-api/src/main changed without any change under tutor-api/src/test."
fi

if grep -E '^tutor-web/(apps|libs)/.*\.ts$' <<<"$files" | grep -qvE '\.(spec|steps|cy)\.ts$|/(e2e|features|support|step-definitions)/' &&
  ! grep -qE '^tutor-web/.*\.(spec|steps|cy)\.ts$|^tutor-web/.*\.feature$' <<<"$files"; then
  fail "tutor-web source changed without any spec or feature change."
fi

if [[ "$failed" == true ]]; then
  exit 1
fi
echo "Roadmap guard passed for $story."
