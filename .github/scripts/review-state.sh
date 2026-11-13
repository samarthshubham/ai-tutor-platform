#!/usr/bin/env bash
# Tracks which commits of a PR Claude has already reviewed, via a marker in one
# sticky PR comment, so each push only reviews what is new.
#
#   review-state.sh range            -> mode/base/head/commits to $GITHUB_OUTPUT (or stdout)
#   review-state.sh save <summary>   -> create or update the sticky summary comment
#                                       (KEEP_HISTORY=true: post a new comment every run,
#                                       so earlier findings stay visible)
#
# Env: REPO (owner/name), PR_NUMBER, HEAD_SHA, BASE_REF (default main), GH_TOKEN.
# REVIEW_NAME (default claude-review) names the sticky comment, so several reviews
# can each keep their own. LAST_REVIEWED_SHA, when set, replaces the PR comment
# lookup (for local testing).
set -euo pipefail

REVIEW_NAME=${REVIEW_NAME:-claude-review}
SUMMARY_TAG="<!-- $REVIEW_NAME summary -->"
SHA_TAG_PREFIX="<!-- $REVIEW_NAME last-reviewed-sha: "

output() {
  echo "$1=$2" >>"${GITHUB_OUTPUT:-/dev/stdout}"
}

summary_comment() {
  gh api --paginate "repos/$REPO/issues/$PR_NUMBER/comments" \
    --jq ".[] | select(.user.type == \"Bot\" and (.body | contains(\"$SUMMARY_TAG\"))) | {id, body}" |
    tail -n 1
}

last_reviewed_sha() {
  if [[ -n "${LAST_REVIEWED_SHA+set}" ]]; then
    echo "$LAST_REVIEWED_SHA"
    return
  fi
  summary_comment | jq -r '.body // ""' |
    sed -n "s/.*$SHA_TAG_PREFIX\([0-9a-f]\{40\}\) -->.*/\1/p" |
    tail -n 1
}

range() {
  local head base last commits
  head=$(git rev-parse "$HEAD_SHA")
  base=$(git merge-base "origin/${BASE_REF:-main}" "$head")
  last=$(last_reviewed_sha)

  output head "$head"
  if [[ -z "$last" ]] || ! git cat-file -e "$last^{commit}" 2>/dev/null ||
    ! git merge-base --is-ancestor "$last" "$head"; then
    output mode full
    output base "$base"
    output commits "$(git rev-list --no-merges --reverse "$base..$head" | tr '\n' ' ')"
    return
  fi

  commits=$(git rev-list --no-merges --reverse "$last..$head" "^origin/${BASE_REF:-main}" | tr '\n' ' ')
  if [[ -z "${commits// /}" ]]; then
    output mode skip
    output base "$last"
    output commits ""
    return
  fi
  output mode incremental
  output base "$last"
  output commits "$commits"
}

save() {
  local summary_file=$1 body existing_id
  [[ -s "$summary_file" ]] || {
    echo "Review summary $summary_file is missing or empty" >&2
    exit 1
  }
  body="$(cat "$summary_file")

$SUMMARY_TAG
$SHA_TAG_PREFIX$(git rev-parse "$HEAD_SHA") -->"
  existing_id=""
  [[ "${KEEP_HISTORY:-false}" == "true" ]] || existing_id=$(summary_comment | jq -r '.id // ""')
  if [[ -n "$existing_id" ]]; then
    gh api -X PATCH "repos/$REPO/issues/comments/$existing_id" -f body="$body" >/dev/null
  else
    gh api -X POST "repos/$REPO/issues/$PR_NUMBER/comments" -f body="$body" >/dev/null
  fi
}

case "${1:-}" in
  range) range ;;
  save) save "${2:?summary file required}" ;;
  *)
    echo "usage: $0 range | save <summary-file>" >&2
    exit 2
    ;;
esac
