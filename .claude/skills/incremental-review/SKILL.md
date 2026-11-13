---
name: incremental-review
description: Review an ai-tutor-platform pull request, but only the commits not reviewed before, against this repo's rules; posts an inline comment for each line-level finding and writes a summary file. Use in the claude-review CI job, or locally with "/incremental-review <pr-number>".
---

# Incremental PR review

Review **only what is new** in a pull request. Code that an earlier run already reviewed is context, not review material, and its findings must not be posted again.

## Inputs

CI passes `pr=<n> mode=<full|incremental> base=<sha> head=<sha> commits="<sha> <sha> …"` (oldest first), computed by `.github/scripts/review-state.sh range`.

Locally, if only a PR number is given, compute them yourself:

```bash
REPO=samarthshubham/ai-tutor-platform PR_NUMBER=<n> HEAD_SHA=$(gh pr view <n> --json headRefOid -q .headRefOid) .github/scripts/review-state.sh range
```

In local runs, **print** the review in the conversation. Do not post comments or write the summary file unless the user asks.

## 1. Read only the range

- `mode=incremental`: run `git show --stat --patch <sha>` for each sha in `commits`. Merge commits are already excluded; they bring in `main`, which was reviewed in its own PRs.
- `mode=full`: run `git diff <base> <head>`. This is the first review, or the branch was force-pushed or rebased.

For `mode=full`, or any range over about 300 changed lines, start with `git diff --stat <base> <head>` and then go **file by file** (`git diff <base> <head> -- <file>`, or `git show <sha> -- <file>`). Read each changed production file in full with Read before you judge it. Don't review a large range from one combined diff.

Open surrounding files with Read, Grep and Glob when you need context. Only lines **added or changed in the range** may receive findings.

## 2. Load what was already said

```bash
gh api --paginate repos/<repo>/pulls/<pr>/comments --jq '.[] | {path, line, body}'
gh api --paginate repos/<repo>/issues/<pr>/comments --jq '.[] | select(.body | contains("<!-- claude-review summary -->")) | .body'
```

Every earlier review run left its own summary comment, so read **all** of them, together with the inline comments and their replies.

- **Do not re-raise** a finding that already exists on the same file and logic, even if its line number moved or the wording would differ.
- If a commit in the range fixes an earlier finding, list it under **Resolved since last review** in the summary.
- If an earlier finding is still open (not fixed, and with no reply giving a reason), list it under **Still open from earlier reviews** with its file and the comment it came from.

## 3. What to look for

Report only findings you are confident are real: a concrete input or state that produces a wrong result, a crash, a security hole, or a broken project rule. No style nits; Spotless and Prettier enforce formatting.

Project rules, from `CLAUDE.md` and `docs/01-module-design.md`:

**Backend (`tutor-api`)**
- A module imports another module only through its `api` package, and only as the table in `docs/01-module-design.md` allows. `org.springframework.ai` appears only under `tutor.ai`, and never in `tutor.ai.api`.
- Controllers return records, never JPA entities. Request bodies use `@Valid`. Errors use the `ErrorResponse` shape (`status, code, message, fields`) through the shared exceptions.
- **Every new or changed endpoint** has a role check and a row per role in an authorization matrix test. Data ownership goes through `AccessPolicy` or `CourseAccess`; a hidden resource is `404`, not `403`.
- Responses to students and parents never carry a reference answer or rubric, and carry the correct option only after grading.
- **No model call inside a database transaction.** Slow AI work runs after commit, asynchronously, with a status that can fail and be retried.
- Every model call goes through `AiCallLogger`. Model output is validated before it is stored; scores are recomputed in code.
- Prompts start with `feature: <AiFeature>`, wrap lesson text and student text in tags, and declare tagged text to be data. No student name or email in a prompt. A prompt whose meaning changes gets a new version file.
- Retrieval filters are built with the filter expression builder and always carry a course restriction.
- Event listeners are idempotent.
- Time comes from the injected `Clock`. No names, emails, answer text or prompt text in `INFO` logs.
- One Flyway file per story, with the number fixed in `docs/05-database.md`; merged migrations are never edited.
- No secrets committed. Constructor injection only. Behaviour changes come with tests; no test calls a real model.

**Frontend (`tutor-web`)**
- Standalone components, signals, `inject()`, built-in control flow. HTTP only through the typed clients in `libs/api`, whose models match the API design including nullable fields.
- Every data page has loading, empty and error states from `libs/ui`.
- Markdown and model output are rendered only through the sanitising pipeline.
- Streams and timers are cleaned up on destroy (an aborted `fetch`, a stopped poll).
- Colours come from Material system tokens. Controls have accessible names.
- Route guards and hidden buttons are UX only; access control must also be enforced by the API.
- Specs updated for changed behaviour.

**Everywhere:** correctness, null handling, error handling that does not swallow failures, injection (SQL, filter expressions, prompt), races between async work and requests, and auth bypasses.

## 4. Be explicit about depth

A short review of a large range is only useful if it says what it skipped. While reviewing, keep track of:
- every changed file, marked **read closely**, **skimmed** or **skipped**. For skimmed and skipped files, say why (generated, lockfiles, realm export, very large diffs).
- what you **could not verify** (tests not run, behaviour that needs a running stack or a real model)

For ranges over ~1,500 changed lines, put security-sensitive and logic-heavy files first: security config, access policy, controllers, services, listeners, the `ai` module. Then tests, then docs.

## 5. Post inline findings (CI only)

**Every** finding that points at a specific added or changed line also gets an inline comment: call `mcp__github_inline_comment__create_inline_comment` on that line. Start it with the same number and severity tag as in the summary (for example "**2. Bug:** …"), give the concrete failure and the fix, and keep it to two to four sentences. A finding about a whole file, or something missing (an untested branch, a missing authorization matrix row), stays in the summary only. Never post an inline comment for a finding you didn't put in the summary.

## 6. Write the summary (CI only)

Write `review-summary.md` in the repository root. The workflow posts it as a **new** PR comment for this run (earlier runs keep theirs) and adds the "last reviewed" marker itself, so **do not** add any `<!-- … -->` markers.

```markdown
## Claude review: <incremental (N new commits) | full (N commits)>

Reviewed `<base short>..<head short>`.

**Scope.** Read closely: <files or areas>. Skimmed: <files>, because <reason>. Not reviewed: <files>, because <reason>.

### Findings
1. **<Bug | Security | Rule>: <one-line claim>.** `path/File.java:42`
   <Concrete scenario: the input or state, then what goes wrong.> <The fix.>
2. …

(If there are none, write: "No new findings in the reviewed range.")

### Security and student data
<Only when auth, ownership checks, student or parent views, logging, prompts or external calls changed. Say what you checked and the result, including what is fine.>

### Project rules
- Module boundaries and Spring AI only in `tutor.ai`: <ok | violation at …>
- Role check + authorization matrix row for new endpoints, `404` for hidden data: <ok | n/a | …>
- No answer key in student or parent responses: <ok | n/a | …>
- No model call in a transaction; calls logged; output validated: <ok | n/a | …>
- Prompt rules (feature line, tagged data, versioning): <ok | n/a | …>
- Frontend: typed clients, three states, sanitised rendering, cleanup: <ok | n/a | …>
- Tests updated with behaviour, no real model in tests: <ok | …>

### Resolved since last review
- <earlier finding>: fixed in `<sha>`. (Omit the section on a first review.)

### Still open from earlier reviews
- <earlier finding, file>: not fixed and not answered. (Omit when there are none.)

### Not verified
- <e.g. tests not run here; behaviour that needs the running stack or a real model>
```

Number the findings and order them by severity. Each one names a file and line, a concrete failure and a fix, and says whether it was also posted inline. If the range has nothing to report, still write the file with the Scope, Rules and Not verified sections. The job fails without it.
