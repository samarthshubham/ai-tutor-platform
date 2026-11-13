# 0004: Slow AI work runs asynchronously

**Status:** accepted

## Context

On the local model, generating five questions takes 20 to 40 seconds and grading one answer 8 to 15 seconds. Holding an HTTP request and a database transaction open that long is fragile.

## Decision

Indexing, question generation and grading run after the request commits, on a bounded executor, triggered by `@TransactionalEventListener(AFTER_COMMIT)` with `@Async`. The request returns `202` with a job or attempt in a pending state. Clients poll the generation job and receive grading results over Server-Sent Events. Model calls never run inside a database transaction. No message broker is used.

## Consequences

- Requests stay fast and transactions short.
- Every piece of async work has a visible status (`RUNNING`, `FAILED`, `GRADING_FAILED`) and a retry endpoint.
- Work in flight is lost on a restart; a startup task marks stale `RUNNING` and `GRADING` rows as failed so they can be retried.
- A broker would be the next step if the application ran as more than one instance.
