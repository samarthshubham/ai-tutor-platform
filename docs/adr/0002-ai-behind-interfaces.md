# 0002: AI behind interfaces

**Status:** accepted

## Context

Model frameworks change quickly, and model calls are slow, non-deterministic and can fail. If Spring AI types leak into business code, every module needs a model to be tested and every framework upgrade touches all of them.

## Decision

Only `tutor.ai` imports `org.springframework.ai`. The rest of the code uses the interfaces and records in `tutor.ai.api`. `ai` depends on no domain module: when it needs domain data it declares a provider interface that a domain module implements.

## Consequences

- Domain services are unit-tested with a mocked `QuestionGenerator` or `AnswerGrader`.
- A Spring AI upgrade, or a move to another framework, changes one module.
- Some mapping code between domain objects and `ai.api` records is needed.
