# 0006: A human reviews AI output that students rely on

**Status:** accepted

## Context

Generated questions can be wrong, ambiguous or off-topic, and an AI grade can be unfair. Both affect what a student learns and how they are judged.

## Decision

- AI-generated questions are stored as `DRAFT` and reach students only after a teacher approves them. The exception is an adaptive set that needs more questions than the bank has: those are generated on demand, shown with an "AI generated" label, and sent to the teacher review queue afterwards.
- AI grades are stored as their own rows. A tutor or teacher can add a human grade, which becomes the effective grade; the AI grade is kept.
- Every AI-produced row stores the prompt version and model that produced it.

## Consequences

- Teachers stay in control of the question bank, and the cost is review time.
- Grade history is auditable, and human overrides give labelled data to evaluate the grading prompt against.
