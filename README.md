# AI Tutor Platform

[![CI](https://github.com/samarthshubham/ai-tutor-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/samarthshubham/ai-tutor-platform/actions/workflows/ci.yml)

> **License notice:** This repository is provided for **viewing and evaluation purposes only.**
> No permission is granted to copy, modify, distribute, sublicense, or use the code in any form
> without explicit written permission from the author. See [LICENSE](./LICENSE) for full terms.

A practice platform for schools and tutors. Teachers publish lessons; a language model writes practice questions from them, grades written answers against a rubric, and answers students' questions about the lesson. People stay in charge: a teacher approves questions before students see them, and a tutor can overrule a grade.

Built as a learning and portfolio project with Java 21, Spring Boot 4.1, Spring AI 2.0, PostgreSQL + pgvector, Keycloak and Angular 21. It runs fully on a laptop with a small local model (Ollama), and can switch to a hosted model with one profile.

## Status

The project is built story by story from a written roadmap. **The design, the backlog and the review pipeline are in place; application code lands with each story.** Progress is on the [project board](https://github.com/users/samarthshubham/projects) and in the [roadmap's tracking table](docs/roadmap/ai-tutor.md#8-github-tracking).

## What it will do

| Feature | How |
|---|---|
| Lessons searchable by meaning | On publish, a lesson is split, embedded and stored in pgvector (RAG) |
| Question generation | The model writes questions from the lesson as structured output; code validates them, asks once for a repair, removes duplicates; a teacher reviews |
| Grading with feedback | Multiple choice is marked in code; written answers are graded by the model per rubric criterion, asynchronously, and the result is pushed over SSE |
| Adaptive practice | Mastery per topic is updated after each grade; the next set targets the weakest topics |
| Tutor chat | Streaming chat with memory and tools that search the course and read the student's own progress; it gives hints, not answers, for open practice questions |
| Five roles | Admin, teacher, tutor, student, parent, with ownership rules enforced by the API |
| Measured quality | Golden-set evaluations on real models, AI call logging, metrics and a dashboard |

## Architecture

```
tutor-app (Angular 21)  --OIDC code flow + PKCE-->  Keycloak
    |  REST JSON + Server-Sent Events
tutor-api (Spring Boot 4.1, Spring AI 2.0, modular monolith)
    |-- PostgreSQL 16 + pgvector        |-- Mailpit
    |-- Ollama or a hosted model API    |-- Prometheus + Grafana
```

Only one module talks to the model; the rest of the code sees plain Java interfaces. Tests and CI use a deterministic fake model and never call a real one.

## Documentation

| Document | Content |
|---|---|
| [Architecture](docs/00-architecture.md) | System, roles, decisions, the core loop |
| [Module design](docs/01-module-design.md) | Modules, dependency rules, events |
| [API design](docs/02-api-design.md) | Every endpoint |
| [AI design](docs/03-ai-design.md) | Interfaces, profiles, prompts, flows, guardrails |
| [Test strategy](docs/04-test-strategy.md) | Levels and gates |
| [Database](docs/05-database.md) | Schema |
| [Design system](docs/08-design-system.md) | Routes and screens |
| [Decisions](docs/adr/) | Six architecture decision records |
| [Roadmap](docs/roadmap/ai-tutor.md) | Goal, requirements, demo data and the full backlog |

## Running it

Prerequisites: Docker, Java 21, Node 22, `curl` and `jq`, and optionally [Ollama](https://ollama.com) with a small chat model and `nomic-embed-text`. The local services (PostgreSQL, Keycloak, Mailpit, Prometheus, Grafana) start with `docker compose -f infra/docker-compose.yml up -d --wait`; see [infra/README.md](infra/README.md) and [CLAUDE.md](CLAUDE.md#commands).
