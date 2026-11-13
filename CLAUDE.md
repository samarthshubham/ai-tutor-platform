# CLAUDE.md

Guidance for Claude Code and other agents working in this repository.

# ai-tutor-platform

A practice platform for schools and tutors: teachers publish lessons, a language model writes practice questions from them, grades written answers against a rubric and answers students' questions, and people stay in charge (teachers approve questions, tutors can overrule grades). A portfolio and learning project: Java 21, Spring Boot 4.1, Spring AI 2.0, PostgreSQL + pgvector, Keycloak, Angular 21.

**State of the build:** the repository is built story by story from `docs/roadmap/ai-tutor.md`. A folder or command below exists only once its story is merged; the roadmap §8 table and the GitHub issues show what is done.

---

## Where the rules live

| Question | Read |
|---|---|
| What to build next, and exactly what a story must deliver | `docs/roadmap/ai-tutor.md` (the story's Goal, Scope, Contract, Notes, AC, Tests) |
| System shape, ports, roles, decisions | `docs/00-architecture.md`, `docs/adr/` |
| Packages, which module may import which, events | `docs/01-module-design.md` |
| Every endpoint, request, response and error code | `docs/02-api-design.md` |
| AI interfaces, profiles, prompts, fake models, flows | `docs/03-ai-design.md` |
| Test levels, tags, gates | `docs/04-test-strategy.md` |
| Tables, columns, which story owns which Flyway file | `docs/05-database.md` |
| Routes, screens, frontend rules | `docs/08-design-system.md` |

The design docs are binding. If the code must differ, change the doc in the same pull request and write "Contract change" in the PR description.

---

## Architecture

```
tutor-app (Angular 21, tutor-web/, :4300)  --OIDC code flow + PKCE-->  Keycloak (:8180, realm tutor)
    |  REST JSON + SSE, Bearer JWT
tutor-api (Spring Boot 4.1, Java 21, Spring AI 2.0, :9190)
    |-- PostgreSQL 16 + pgvector (:5442, db tutor)      |-- Mailpit (SMTP :1125, UI :8125)
    |-- Ollama (:11434) or a hosted model API           |-- Prometheus (:9191), Grafana (:3100)
```

tutor-api is a modular monolith: `common`, `identity`, `course`, `cms`, `practice`, `mastery`, `ai`, `notify` under `io.github.samarthshubham.tutor`. Each module exposes only its `api` package.

## Repository layout

```
tutor-api/     Spring Boot backend (Maven wrapper)            from E0-S3
tutor-web/     Angular 21 Nx workspace: apps/tutor-app, apps/tutor-app-e2e,
               apps/seed-demo-data, libs/auth, libs/api, libs/ui, libs/e2e-support   from E0-S4
infra/         docker-compose.yml, keycloak/, postgres/, prometheus/, grafana/      from E0-S2
scripts/       bench-llm.sh                                    from E0-S6
dev.sh         start, stop, status, logs, seed                 from E0-S5
docs/          design docs, ADRs, roadmap, study guide
.github/       workflows and their scripts
.claude/skills incremental-review, roadmap-review (used by the CI review jobs)
```

---

## Rules that are easy to break

**Backend**
- A module imports another module only through its `api` package and only as `docs/01-module-design.md` allows. `ModuleBoundaryTest` enforces it.
- `org.springframework.ai` is imported only under `tutor.ai`, and never in `tutor.ai.api`. `ai` imports no domain module.
- **Spring Boot 4.1 and Spring AI 2.0 are newer than most examples.** Check the current reference docs or the jar source before using a Spring AI, Spring Security 7 or Jackson 3 API. Do not write it from memory, and do not downgrade to make an old example compile.
- No model call inside a database transaction. Slow AI work runs after commit, asynchronously, with a status that can fail and be retried.
- Every model call goes through `AiCallLogger`. Model output is validated in code before it is stored.
- No test and no CI job calls a real model: use the `fake-ai` profile and `FakeChatModel`.
- Every endpoint has a role check and a row per role in an authorization matrix test. Ownership goes through `AccessPolicy` or `CourseAccess`; hidden data is `404`.
- Student and parent responses never carry a reference answer or rubric.
- Controllers return records, never entities. Errors go through the shared exceptions and `ErrorResponse`.
- Time comes from the injected `Clock`. Ids are UUIDs.
- One Flyway file per story with the number fixed in `docs/05-database.md`. Never edit a merged migration.
- No names, emails, answer text or prompt text in `INFO` logs or in `ai_call_log`.
- Constructor injection only.

**Frontend**
- Standalone components, signals, `inject()`, built-in control flow.
- HTTP only through the typed clients in `libs/api`; models match the API design, including nullable fields.
- Every data page has loading, empty and error states from `libs/ui`.
- Markdown and model output are rendered only through the sanitising pipeline.
- Clean up streams and timers on destroy.

**Everywhere**
- Reuse before writing: search for an existing class, component or helper first.
- No code comments unless the reason is not obvious from the code.
- The smallest change that delivers the story. No unrelated refactors, no work a story lists as out of scope.
- Never lower a coverage gate, disable a test or edit a story's AC to make a check pass.
- No secrets in the repository. The hosted model key comes from `ANTHROPIC_API_KEY`.

---

## Commands

Each block works once the story in brackets is merged.

```bash
# Infrastructure (E0-S2)
docker compose -f infra/docker-compose.yml up -d --wait
bash infra/smoke.sh
docker compose -f infra/docker-compose.yml down        # keep data; add -v to wipe

# tutor-api (E0-S3)
cd tutor-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,fake-ai   # or dev,ollama / dev,cloud
./mvnw verify            # all tests, 90% line gate, Spotless check
./mvnw spotless:apply

# tutor-web (E0-S4)
cd tutor-web
npx nx serve tutor-app
npx nx run-many -t lint test build
npx nx e2e tutor-app-e2e                               # needs the stack running (E0-S5)
npx nx run seed-demo-data:seed                         # (E2-S4)

# Whole stack (E0-S5)
./dev.sh            # start; Ctrl+C stops. Never run it under `timeout`.
./dev.sh stop | status | logs <service> | seed

# Workflow scripts
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
REPO=samarthshubham/ai-tutor-platform python3 -I .github/scripts/create-issues.py --dry-run
```

Demo users (dev realm only): `admin1`, `teacher1`, `teacher2`, `tutor1`, `student1`, `student2`, `student3`, `parent1`, `parent2`. Password: the role in title case plus `#2026`, for example `Teacher#2026`. Details in roadmap §5.

AI profiles: `ollama` (local, default when Ollama is running), `cloud` (hosted chat model, needs `ANTHROPIC_API_KEY`), `fake-ai` (deterministic, used by tests, CI and e2e). Exactly one is active.

---

## Working on a story

1. Pick a story whose `Blocked by` stories are all merged. Read its section in the roadmap and every doc it names.
2. Branch from an up-to-date `main`: `feat/e2-s1-courses` (type, story id, a few words).
3. One story = one pull request. Use the template: `Roadmap story: E2-S1` and `Closes #<issue>`.
4. Small commits with a Conventional Commit subject (`feat: add course enrollment`). No AI attribution in commits, pull requests or comments.
5. Before every push: `./mvnw spotless:apply && ./mvnw verify` for backend changes; `npx nx run-many -t lint test build` for web changes.
6. Tick an AC or task box in the issue only after the code for it is pushed. Never change the AC wording in an issue; if scope must change, change the roadmap and the issue together in the pull request and say so.
7. Checks that must pass: `backend`, `frontend`, `coverage-gate`, `issue-sync`, `roadmap-review` (must say Aligned), and `e2e` once it exists. Answer every `claude-review` finding: fix it and reply "Fixed in <sha>: <what changed>", or reply with the reason for not changing.
8. Before calling a story done, run it the way a user would: `./dev.sh`, seed twice, click through the feature with the right demo user, and look at the browser console.

Do not run the `roadmap-review` or `incremental-review` skills locally against a pull request; they post to it and duplicate CI.
