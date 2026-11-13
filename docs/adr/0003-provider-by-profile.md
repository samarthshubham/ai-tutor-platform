# 0003: Model provider chosen by profile

**Status:** accepted

## Context

The development machine has a 4 GB GPU, which fits a 3B-parameter model. Small models are slow and weaker at structured output and tool calling. Tests and CI must not depend on any model.

## Decision

Three Spring profiles: `ollama` (local chat and embedding models), `cloud` (hosted chat model, local embedding model) and `fake-ai` (deterministic fakes). Embeddings always use the same 768-dimension model so stored vectors stay valid across profiles.

## Consequences

- The same code runs offline for free, with a stronger model for demos, and deterministically in CI.
- Changing the embedding model means re-indexing every lesson; that is a deliberate, separate step.
- Prompts must work on the weakest model, so they are short and the output is validated and repaired in code.
