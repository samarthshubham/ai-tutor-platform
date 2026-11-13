# 0001: Modular monolith

**Status:** accepted

## Context

The platform has seven feature areas and one developer. It must run on a laptop next to a local model. The interesting engineering is in the AI flows, not in service-to-service plumbing.

## Decision

One Spring Boot application, split into modules (`identity`, `course`, `cms`, `practice`, `mastery`, `ai`, `notify`, `common`). A module exposes only its `api` package. Modules call each other through those interfaces or react to Spring application events. An ArchUnit test enforces the allowed dependencies listed in `docs/01-module-design.md`.

## Consequences

- One process, one database, one transaction manager: simple local runs and simple tests.
- Boundaries are real because the build fails when they are crossed, so a module can be extracted into a service later.
- Everything scales together. The first thing to extract would be `ai`, because model calls are slow and have different scaling needs.
- Events are in-process. An event is lost if the process dies between commit and listener; jobs and attempts therefore keep a status that makes stuck work visible and retryable.
