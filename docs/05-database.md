# Database

PostgreSQL 16 with the `vector` extension (image `pgvector/pgvector:pg16`), database `tutor`, user `tutor`. Keycloak uses a second database, `keycloak`, in the same server. Hibernate runs with `ddl-auto: validate`; **Flyway owns the schema**.

## Rules

- One migration file per story, numbered in the table below. The number is fixed here so stories built in parallel never collide. Never edit a merged migration; add a new one.
- `spring.flyway.out-of-order` is `true` only in the `dev` profile, so a local database survives stories merging out of number order.
- All ids are `uuid`. All timestamps are `timestamptz not null` unless marked nullable. Enum columns are `varchar(32)` with a `check` constraint listing the values.
- Every foreign key has an index. Every table has `created_at`.
- `jsonb` is used only for value lists that are always read and written whole (options, rubric, misconceptions).

## Migration ownership

| File | Story | Creates |
|---|---|---|
| `V1__baseline.sql` | E0-S3 | extensions `vector`, `pgcrypto` |
| `V2__ai_call_log.sql` | E0-S6 | `ai_call_log` |
| `V3__identity.sql` | E1-S1 | `school`, `app_user` |
| `V4__relationships.sql` | E1-S2 | `guardian_link`, `tutor_assignment` |
| `V5__course.sql` | E2-S1 | `course`, `topic`, `enrollment` |
| `V6__lesson.sql` | E2-S2 | `lesson`, `lesson_version` |
| `V7__content_index.sql` | E3-S1 | `vector_store`, `lesson_index` |
| `V8__question.sql` | E4-S3 | `question`, `generation_job` |
| `V9__practice.sql` | E5-S1 | `practice_session`, `session_item`, `attempt` |
| `V10__grade.sql` | E5-S2 | `grade` |
| `V11__mastery.sql` | E6-S1 | `topic_mastery` |
| `V12__chat.sql` | E7-S1 | `chat_conversation`, Spring AI chat memory table |
| `V13__notification.sql` | E8-S1 | `notification` |
| `V14__notification_preference.sql` | E8-S2 | `notification_preference` |

## Tables

`PK` = primary key, `FK` = foreign key, `UQ` = unique.

### ai_call_log (V2)

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| feature | varchar(32) | `PING, INDEXING, SEARCH, QUESTION_GEN, GRADING, CHAT` |
| prompt_version | varchar(64) null | for example `grading.v1` |
| provider | varchar(32) | `ollama, anthropic, fake` |
| model | varchar(64) | |
| user_id | uuid null | the app user who caused the call; no FK so the log outlives users |
| prompt_tokens | int null | |
| completion_tokens | int null | |
| latency_ms | int | |
| outcome | varchar(32) | `OK, INVALID_OUTPUT, TIMEOUT, ERROR` |
| error | varchar(500) null | exception class and short message, no prompt text |
| created_at | timestamptz | index on `(feature, created_at)` |

### school, app_user (V3)

| Table | Column | Type | Notes |
|---|---|---|---|
| school | id | uuid PK | one row seeded by the migration: id `00000000-0000-0000-0000-000000000001`, name `Riverside Academy` |
| | name | varchar(200) | |
| | created_at | timestamptz | |
| app_user | id | uuid PK | |
| | keycloak_id | varchar(64) UQ | the token's `sub` |
| | username | varchar(100) UQ | `preferred_username` |
| | display_name | varchar(200) | token `name` |
| | email | varchar(320) | |
| | role | varchar(32) | `ADMIN, TEACHER, TUTOR, STUDENT, PARENT` |
| | school_id | uuid FK school | |
| | created_at, last_seen_at | timestamptz | |

### guardian_link, tutor_assignment (V4)

| Table | Column | Type | Notes |
|---|---|---|---|
| guardian_link | id | uuid PK | |
| | parent_id, student_id | uuid FK app_user | UQ `(parent_id, student_id)` |
| | created_at | timestamptz | |
| tutor_assignment | id | uuid PK | |
| | tutor_id, student_id | uuid FK app_user | UQ `(tutor_id, student_id)` |
| | created_at | timestamptz | |

### course, topic, enrollment (V5)

| Table | Column | Type | Notes |
|---|---|---|---|
| course | id | uuid PK | |
| | school_id | uuid FK school | |
| | teacher_id | uuid FK app_user | |
| | title | varchar(200) | |
| | subject | varchar(100) | |
| | grade_level | int | 1 to 12 |
| | description | varchar(2000) null | |
| | status | varchar(32) | `ACTIVE, ARCHIVED` |
| | created_at, updated_at | timestamptz | |
| topic | id | uuid PK | |
| | course_id | uuid FK course | UQ `(course_id, name)` |
| | name | varchar(200) | |
| | position | int | |
| | created_at | timestamptz | |
| enrollment | course_id, student_id | uuid FK | composite PK |
| | created_at | timestamptz | |

### lesson, lesson_version (V6)

| Table | Column | Type | Notes |
|---|---|---|---|
| lesson | id | uuid PK | |
| | course_id | uuid FK course | |
| | topic_id | uuid FK topic | |
| | author_id | uuid FK app_user | |
| | title | varchar(200) | |
| | body_markdown | text | the working draft |
| | status | varchar(32) | `DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED` |
| | published_version | int null | newest published version number |
| | review_comment | varchar(2000) null | set by request-changes, cleared on submit |
| | published_at | timestamptz null | |
| | created_at, updated_at | timestamptz | |
| | version | bigint | JPA `@Version` optimistic lock |
| lesson_version | id | uuid PK | |
| | lesson_id | uuid FK lesson | UQ `(lesson_id, version)` |
| | version | int | starts at 1 |
| | title | varchar(200) | snapshot |
| | body_markdown | text | snapshot |
| | published_by | uuid FK app_user | |
| | created_at | timestamptz | |

### vector_store, lesson_index (V7)

`vector_store` has the layout Spring AI's `PgVectorStore` expects, created by Flyway (`spring.ai.vectorstore.pgvector.initialize-schema` is `false`).

| Table | Column | Type | Notes |
|---|---|---|---|
| vector_store | id | uuid PK | default `gen_random_uuid()` |
| | content | text | the chunk |
| | metadata | json | keys: `lessonId, lessonVersion, lessonTitle, courseId, topicId, chunkIndex` (all strings except `lessonVersion`, `chunkIndex`) |
| | embedding | vector(768) | HNSW index, cosine distance |
| lesson_index | lesson_id | uuid PK, FK lesson | |
| | version | int | lesson version that was indexed |
| | status | varchar(32) | `INDEXING, INDEXED, FAILED` (no row means `NOT_INDEXED`) |
| | chunk_count | int | |
| | error | varchar(500) null | |
| | indexed_at | timestamptz null | |
| | created_at, updated_at | timestamptz | |

### question, generation_job (V8)

| Table | Column | Type | Notes |
|---|---|---|---|
| question | id | uuid PK | |
| | course_id | uuid FK course | |
| | topic_id | uuid FK topic | |
| | lesson_id | uuid FK lesson null | |
| | generation_job_id | uuid FK generation_job null | |
| | type | varchar(32) | `MCQ, SHORT, WRITING` |
| | difficulty | int | 1 to 5 |
| | stem | varchar(2000) | |
| | options | jsonb null | array of 4 strings for `MCQ` |
| | correct_option | int null | 0 to 3 |
| | reference_answer | text null | |
| | rubric | jsonb null | `[{"criterion": "...", "maxPoints": 2}]` |
| | source | varchar(32) | `AI, MANUAL` |
| | status | varchar(32) | `DRAFT, APPROVED, REJECTED, RETIRED` |
| | prompt_version | varchar(64) null | |
| | source_chunk_ids | jsonb null | array of `vector_store.id` |
| | stem_embedding | vector(768) null | for duplicate detection |
| | reject_reason | varchar(500) null | |
| | created_by | uuid FK app_user | |
| | reviewed_by | uuid FK app_user null | |
| | created_at, updated_at | timestamptz | index `(course_id, topic_id, status)` |
| generation_job | id | uuid PK | |
| | requested_by | uuid FK app_user | |
| | course_id, lesson_id, topic_id | uuid FK | |
| | type | varchar(32) | |
| | difficulty | int | |
| | count | int | 1 to 10 |
| | status | varchar(32) | `PENDING, RUNNING, DONE, FAILED` |
| | created_count | int | default 0 |
| | skipped_duplicates | int | default 0 |
| | error | varchar(500) null | |
| | created_at | timestamptz | |
| | finished_at | timestamptz null | |

### practice_session, session_item, attempt (V9)

| Table | Column | Type | Notes |
|---|---|---|---|
| practice_session | id | uuid PK | |
| | student_id | uuid FK app_user | index `(student_id, status)` |
| | course_id | uuid FK course | |
| | mode | varchar(32) | `SELF, ADAPTIVE, ASSIGNED` |
| | title | varchar(200) | |
| | status | varchar(32) | `IN_PROGRESS, SUBMITTED, GRADED` |
| | assigned_by | uuid FK app_user null | |
| | due_at | timestamptz null | |
| | average_score | numeric(5,2) null | set when `GRADED` |
| | created_at | timestamptz | |
| | completed_at | timestamptz null | |
| session_item | session_id | uuid FK practice_session | PK `(session_id, position)` |
| | position | int | starts at 1 |
| | question_id | uuid FK question | UQ `(session_id, question_id)` |
| attempt | id | uuid PK | |
| | session_id | uuid FK practice_session | UQ `(session_id, question_id)` |
| | question_id | uuid FK question | |
| | student_id | uuid FK app_user | index `(student_id, question_id)` |
| | status | varchar(32) | `DRAFT, GRADING, GRADED, GRADING_FAILED` |
| | draft_text | text null | |
| | answer_text | text null | |
| | selected_option | int null | |
| | draft_saved_at | timestamptz null | |
| | submitted_at | timestamptz null | |
| | created_at | timestamptz | |
| | version | bigint | JPA `@Version` |

One `attempt` row is created per item when the session is created.

### grade (V10)

| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| attempt_id | uuid FK attempt | index |
| source | varchar(32) | `AUTO, AI, HUMAN` |
| score | numeric(5,2) | 0 to 100 |
| feedback | text null | |
| per_criterion | jsonb null | `[{"criterion","points","maxPoints","comment"}]` |
| misconceptions | jsonb null | array of strings |
| grader_id | uuid FK app_user null | set for `HUMAN` |
| prompt_version | varchar(64) null | |
| model | varchar(64) null | |
| created_at | timestamptz | |

Grades are append-only. The effective grade is the newest `HUMAN` grade, otherwise the newest other grade.

### topic_mastery (V11)

| Column | Type | Notes |
|---|---|---|
| student_id | uuid FK app_user | PK `(student_id, topic_id)` |
| topic_id | uuid FK topic | |
| mastery | numeric(4,3) | 0 to 1 |
| attempts | int | |
| last_attempt_id | uuid null | makes the update idempotent per attempt and grade source |
| created_at, updated_at | timestamptz | |

### chat_conversation and chat memory (V12)

| Table | Column | Type | Notes |
|---|---|---|---|
| chat_conversation | id | uuid PK | also the Spring AI conversation id |
| | student_id | uuid FK app_user | index |
| | course_id | uuid FK course | |
| | title | varchar(200) | first user message, cut to 60 characters |
| | created_at, updated_at | timestamptz | |

The message history lives in the table used by Spring AI's JDBC chat memory repository. V12 creates it from the schema script shipped in the Spring AI jar for PostgreSQL (copy it; `initialize-schema` stays `never`).

### notification (V13), notification_preference (V14)

| Table | Column | Type | Notes |
|---|---|---|---|
| notification | id | uuid PK | |
| | user_id | uuid FK app_user | index `(user_id, read_at)` |
| | type | varchar(32) | `LESSON_PUBLISHED, PRACTICE_ASSIGNED, ATTEMPT_GRADED, SESSION_GRADED, QUESTIONS_GENERATED` |
| | title | varchar(200) | |
| | body | varchar(1000) | |
| | link | varchar(300) null | web route |
| | dedupe_key | varchar(200) | UQ `(user_id, dedupe_key)`; an event delivered twice makes one row |
| | read_at | timestamptz null | |
| | emailed_at | timestamptz null | |
| | created_at | timestamptz | |
| notification_preference | user_id | uuid PK, FK app_user | |
| | email_enabled | boolean | default true |
| | muted_types | jsonb | array of type names, default `[]` |
| | created_at, updated_at | timestamptz | |

## Demo data

The migrations seed only the school. Everything else comes from `tutor-web/apps/seed-demo-data` through the API (roadmap §5), so the seed also proves the API works.
