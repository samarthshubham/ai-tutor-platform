# Architecture

AI Tutor Platform is a practice platform for schools and tutors. Teachers publish lessons; the platform turns them into practice questions, grades written answers, adapts the next practice set to each student's weak topics, and gives students a tutor chat grounded in their course content.

## System

```
tutor-app (Angular 21, Nx workspace tutor-web/, :4300)
    |  OIDC authorization code + PKCE            --> Keycloak 26 (:8180, realm "tutor")
    |  REST JSON + Server-Sent Events, Bearer JWT
tutor-api (Spring Boot 4.1, Java 21, Spring AI 2.0, :9190)
    |  JPA / JDBC                                  --> PostgreSQL 16 + pgvector (:5442, database "tutor")
    |  SMTP                                        --> Mailpit (:1125 SMTP, :8125 UI)
    |  Spring AI ChatModel / EmbeddingModel        --> Ollama (:11434) or a hosted model API
    |  /actuator/prometheus                        <-- Prometheus (:9191) --> Grafana (:3100)
```

Ports are chosen so this stack can run next to `clinic-fhir-system` on the same machine.

## Decisions

| Decision | Choice | ADR |
|---|---|---|
| Deployment shape | One Spring Boot application split into feature modules (modular monolith) | [0001](adr/0001-modular-monolith.md) |
| AI boundary | Only the `ai` module imports Spring AI; the rest of the code sees plain Java interfaces and records | [0002](adr/0002-ai-behind-interfaces.md) |
| Model provider | Chosen by Spring profile: `ollama`, `cloud` or `fake-ai`; embeddings always come from the same embedding model | [0003](adr/0003-provider-by-profile.md) |
| Slow AI work | Question generation and grading run asynchronously after commit; clients poll a job or listen on SSE | [0004](adr/0004-async-ai-work.md) |
| Identity | Keycloak issues tokens; tutor-api is an OAuth2 resource server and keeps its own `app_user` row per user | [0005](adr/0005-keycloak-resource-server.md) |
| Human in the loop | AI-generated questions need teacher approval before students see them, except in adaptive sets where they are labelled | [0006](adr/0006-human-review-of-ai-output.md) |

## Roles

| Role | Can |
|---|---|
| `ADMIN` | Manage users and links, review and publish lessons, see AI usage |
| `TEACHER` | Manage own courses, topics and lessons; generate and review questions; see class progress |
| `TUTOR` | See assigned students, assign practice, override AI grades |
| `STUDENT` | Read published lessons of enrolled courses, practise, see own progress, use tutor chat |
| `PARENT` | Read progress and notifications of linked children |

A user has exactly one role.

## Request flow for the core loop

1. A teacher writes a lesson and submits it; an admin publishes it.
2. Publishing emits `LessonPublished`. After commit, the lesson is chunked, embedded and stored in pgvector.
3. The teacher starts a generation job. The job retrieves the lesson's chunks, asks the model for questions as structured output, validates them and stores them as `DRAFT`. The teacher approves or rejects each.
4. A student starts a practice session and writes answers on the writing board, which autosaves drafts.
5. On submit, multiple choice answers are graded in code; written answers are graded by the model against the rubric. The result arrives over SSE.
6. Each grade updates the student's mastery of that topic. The next adaptive set targets the weakest topics.
7. The student, their parents and their tutor see progress; notifications go out in-app and by email.

## Documents

| File | Content |
|---|---|
| [01-module-design.md](01-module-design.md) | Modules, layer rules, events |
| [02-api-design.md](02-api-design.md) | Every REST and SSE endpoint |
| [03-ai-design.md](03-ai-design.md) | AI interfaces, prompts, profiles, fake model |
| [04-test-strategy.md](04-test-strategy.md) | Test levels and what each story must deliver |
| [05-database.md](05-database.md) | Schema and Flyway ownership |
| [08-design-system.md](08-design-system.md) | Routes, screens and shared UI rules |
| [roadmap/ai-tutor.md](roadmap/ai-tutor.md) | Goal, NFRs, demo data and the backlog |
