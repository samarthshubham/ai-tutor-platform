# API design

Base URL `http://localhost:9190`. All paths below start with `/api`. Requests and responses are JSON unless marked SSE. Every endpoint needs a Keycloak access token (`Authorization: Bearer <jwt>`) except `/actuator/health`, `/actuator/prometheus`, `/v3/api-docs/**` and `/swagger-ui/**`.

The **Story** column names the roadmap story that builds the endpoint.

## 1. Conventions

**Ids** are UUID strings. **Timestamps** are ISO-8601 UTC (`2026-11-13T15:45:00Z`).

**Paging:** `?page=0&size=20` (size max 100). Response:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

**Errors:** every non-2xx response has this body. `fields` appears only for validation errors.

```json
{ "status": 400, "code": "VALIDATION_FAILED", "message": "Request is not valid", "fields": { "title": "must not be blank" } }
```

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation or a business validation rule failed |
| 401 | `UNAUTHENTICATED` | Missing, expired or invalid token |
| 403 | `FORBIDDEN` | The role may not call this endpoint |
| 404 | `NOT_FOUND` | The resource does not exist, or the caller may not see it |
| 409 | `CONFLICT` | Wrong state for this action, or a duplicate |
| 429 | `RATE_LIMITED` | Too many AI requests; `Retry-After` header set |
| 503 | `AI_UNAVAILABLE` | The model timed out or could not be reached |
| 405, 406, 415, … | the status name, e.g. `METHOD_NOT_ALLOWED` | Other HTTP protocol errors raised by the framework (wrong method, unsupported media type); the status is kept |
| 500 | `INTERNAL_ERROR` | Anything else; the message is generic |

**Roles** in the tables: A = ADMIN, T = TEACHER, U = TUTOR, S = STUDENT, P = PARENT. "own" means limited by data ownership: a teacher's own courses, a tutor's assigned students, a parent's linked children, a student's own data.

## 2. Identity

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/me` | all | E1-S1 | Current user; creates the `app_user` row on first call |
| GET | `/users?role=&q=` | A | E1-S2 | Page of users in the school |
| GET | `/users/{id}` | A | E1-S2 | One user |
| GET | `/guardian-links?studentId=&parentId=` | A | E1-S2 | List links |
| POST | `/guardian-links` | A | E1-S2 | Link a parent to a student |
| DELETE | `/guardian-links/{id}` | A | E1-S2 | Remove a link |
| GET | `/tutor-assignments?tutorId=&studentId=` | A | E1-S2 | List assignments |
| POST | `/tutor-assignments` | A | E1-S2 | Assign a tutor to a student |
| DELETE | `/tutor-assignments/{id}` | A | E1-S2 | Remove an assignment |
| GET | `/me/students` | U, P | E1-S2 | A tutor's assigned students or a parent's children |

```json
// User (GET /me, /users/{id})
{ "id": "…", "username": "teacher1", "displayName": "Tara Teacher", "email": "teacher1@tutor.local", "role": "TEACHER", "schoolId": "…" }
// POST /guardian-links          -> 201 { "id": "…", "parentId": "…", "studentId": "…" }
{ "parentId": "…", "studentId": "…" }
// POST /tutor-assignments       -> 201 { "id": "…", "tutorId": "…", "studentId": "…" }
{ "tutorId": "…", "studentId": "…" }
```

Linking a user with the wrong role (for example a teacher as `parentId`) is `400`. A duplicate link is `409`.

## 3. Courses

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/courses` | all, own | E2-S1 | Page of courses the caller may see |
| POST | `/courses` | A, T | E2-S1 | Create; a teacher becomes its teacher, an admin passes `teacherId` |
| GET | `/courses/{id}` | all, own | E2-S1 | Course with its topics |
| PUT | `/courses/{id}` | A, T own | E2-S1 | Update |
| POST | `/courses/{id}/archive` | A, T own | E2-S1 | Archive |
| POST | `/courses/{id}/topics` | A, T own | E2-S1 | Add a topic |
| PUT | `/topics/{id}` | A, T own | E2-S1 | Rename or reorder |
| DELETE | `/topics/{id}` | A, T own | E2-S1 | Delete; `409` when lessons or questions use it |
| GET | `/courses/{id}/students` | A, T own | E2-S1 | Enrolled students |
| POST | `/courses/{id}/enrollments` | A, T own | E2-S1 | Enroll a student |
| DELETE | `/courses/{id}/enrollments/{studentId}` | A, T own | E2-S1 | Remove |

```json
// POST /courses
{ "title": "Grade 8 Science", "subject": "Science", "gradeLevel": 8, "description": "…", "teacherId": null }
// Course
{ "id": "…", "title": "Grade 8 Science", "subject": "Science", "gradeLevel": 8, "description": "…",
  "teacherId": "…", "teacherName": "Tara Teacher", "status": "ACTIVE",
  "topics": [ { "id": "…", "name": "Photosynthesis", "position": 1 } ] }
// POST /courses/{id}/topics   { "name": "Photosynthesis", "position": 1 }
// POST /courses/{id}/enrollments   { "studentId": "…" }
```

Course visibility: admin sees all in the school; teacher sees courses they teach; student sees enrolled courses; tutor and parent see courses their students are enrolled in.

## 4. Lessons (CMS)

Status flow: `DRAFT -> IN_REVIEW -> PUBLISHED -> ARCHIVED`. `request-changes` moves `IN_REVIEW -> DRAFT`. `revise` on a `PUBLISHED` lesson moves it to `DRAFT` while students keep seeing the last published version. A wrong transition is `409`.

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/courses/{courseId}/lessons?status=` | all, own | E2-S2 | Lessons of a course; students, tutors and parents get only lessons that have a published version |
| POST | `/courses/{courseId}/lessons` | T own | E2-S2 | Create as `DRAFT` |
| GET | `/lessons/{id}` | all, own | E2-S2 | Staff: draft and status. Others: the published version |
| PUT | `/lessons/{id}` | T own | E2-S2 | Edit; only in `DRAFT` |
| POST | `/lessons/{id}/submit` | T own | E2-S2 | `DRAFT -> IN_REVIEW` |
| POST | `/lessons/{id}/publish` | A | E2-S2 | `IN_REVIEW -> PUBLISHED`, snapshots a version |
| POST | `/lessons/{id}/request-changes` | A | E2-S2 | `IN_REVIEW -> DRAFT` with a comment |
| POST | `/lessons/{id}/revise` | T own | E2-S2 | `PUBLISHED -> DRAFT`, published version stays live |
| POST | `/lessons/{id}/archive` | A, T own | E2-S2 | Any status to `ARCHIVED` |
| GET | `/lessons/{id}/versions` | A, T own | E2-S2 | Published versions, newest first |
| GET | `/lessons?status=IN_REVIEW` | A | E2-S2 | Review queue across courses |

```json
// POST /courses/{courseId}/lessons, PUT /lessons/{id}
{ "title": "How plants make food", "topicId": "…", "bodyMarkdown": "# Photosynthesis\n…" }
// Lesson (staff view)
{ "id": "…", "courseId": "…", "topicId": "…", "title": "How plants make food", "status": "IN_REVIEW",
  "bodyMarkdown": "…", "publishedVersion": 1, "reviewComment": null,
  "authorId": "…", "updatedAt": "…", "publishedAt": "…" }
// Lesson (student view): no status, no draft
{ "id": "…", "courseId": "…", "topicId": "…", "title": "…", "bodyMarkdown": "…", "version": 1, "publishedAt": "…" }
// POST /lessons/{id}/request-changes   { "comment": "Add a worked example" }
```

`bodyMarkdown` is 1 to 50,000 characters.

## 5. Content index (RAG)

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/lessons/{id}/index-status` | A, T own | E3-S1 | State of the lesson's embeddings |
| POST | `/lessons/{id}/reindex` | A, T own | E3-S1 | Re-run indexing; `202`; `409` when the lesson has no published version |
| POST | `/content-search` | A, T own, S own | E3-S2 | Semantic search inside one course |

```json
// index status
{ "lessonId": "…", "version": 1, "status": "INDEXED", "chunkCount": 14, "indexedAt": "…", "error": null }
// status is NOT_INDEXED | INDEXING | INDEXED | FAILED
// POST /content-search
{ "courseId": "…", "query": "why are leaves green", "lessonId": null, "topK": 5 }
// -> 200
[ { "chunkId": "…", "lessonId": "…", "lessonTitle": "How plants make food", "topicId": "…", "text": "…", "score": 0.83 } ]
```

`topK` is 1 to 10, default 5. Search returns only chunks of lessons in a course the caller may see.

## 6. Question bank and generation

Question status: `DRAFT -> APPROVED | REJECTED`, `APPROVED -> RETIRED`. Types: `MCQ`, `SHORT`, `WRITING`. Difficulty 1 (easiest) to 5.

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| POST | `/question-generation-jobs` | T own | E4-S3 | Start a job; `202` |
| GET | `/question-generation-jobs/{id}` | T own, A | E4-S3 | Job state |
| GET | `/question-generation-jobs?courseId=` | T own, A | E4-S3 | Recent jobs |
| GET | `/questions?courseId=&topicId=&status=&type=&source=` | A, T own, U own | E4-S3 | Page of questions |
| POST | `/questions` | T own | E4-S3 | Create a manual question (`APPROVED` at once) |
| GET | `/questions/{id}` | A, T own, U own | E4-S3 | One question with answer and rubric |
| PUT | `/questions/{id}` | T own | E4-S3 | Edit a `DRAFT` or `APPROVED` question |
| POST | `/questions/{id}/approve` | T own | E4-S3 | `DRAFT -> APPROVED` |
| POST | `/questions/{id}/reject` | T own | E4-S3 | `DRAFT -> REJECTED` with a reason |
| POST | `/questions/{id}/retire` | T own | E4-S3 | `APPROVED -> RETIRED` |

```json
// POST /question-generation-jobs
{ "lessonId": "…", "topicId": "…", "type": "MCQ", "difficulty": 3, "count": 5 }
// Job
{ "id": "…", "lessonId": "…", "topicId": "…", "type": "MCQ", "difficulty": 3, "count": 5,
  "status": "DONE", "createdCount": 5, "skippedDuplicates": 0, "error": null,
  "createdAt": "…", "finishedAt": "…" }
// status is PENDING | RUNNING | DONE | FAILED
// Question (staff view)
{ "id": "…", "courseId": "…", "topicId": "…", "lessonId": "…", "type": "MCQ", "difficulty": 3,
  "stem": "Which gas do plants take in during photosynthesis?",
  "options": ["Oxygen", "Carbon dioxide", "Nitrogen", "Hydrogen"], "correctOption": 1,
  "referenceAnswer": "Carbon dioxide, taken in through the stomata.",
  "rubric": [ { "criterion": "Names the gas", "maxPoints": 1 } ],
  "source": "AI", "status": "DRAFT", "promptVersion": "question-generation.v1",
  "sourceChunkIds": ["…"], "rejectReason": null, "createdAt": "…" }
// POST /questions/{id}/reject   { "reason": "Ambiguous wording" }
```

`count` is 1 to 10. For `MCQ`: exactly 4 distinct options and `correctOption` 0 to 3; no rubric needed. For `SHORT` and `WRITING`: `referenceAnswer` and a rubric of 1 to 6 criteria whose `maxPoints` (1 to 10 each) are required; `options` and `correctOption` are null. A job for a lesson that is not `INDEXED` is `409`.

Students never call `/questions`. They get questions through practice sessions in the **student view**: `id, type, difficulty, stem, options, topicId, aiGenerated`.

## 7. Practice

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| POST | `/practice-sessions` | S | E5-S1 (`SELF`), E6-S2 (`ADAPTIVE`) | Start a session |
| POST | `/practice-assignments` | U own, T own | E5-S1 | Create an `ASSIGNED` session for a student |
| GET | `/practice-sessions?studentId=&status=` | S own, U own, T own, P own, A | E5-S1 | Page of sessions |
| GET | `/practice-sessions/{id}` | same | E5-S1 | Session with items and attempts |
| PUT | `/attempts/{id}/draft` | S own | E5-S1 | Autosave the writing board |
| POST | `/attempts/{id}/submit` | S own | E5-S2 | Submit one answer; `202` |
| POST | `/practice-sessions/{id}/submit` | S own | E5-S2 | Submit every unsubmitted attempt |
| GET | `/practice-sessions/{id}/events` | S own | E5-S3 | SSE stream of grading results |
| GET | `/attempts/{id}` | S own, U own, T own, P own, A | E5-S2 | Attempt with grades |
| POST | `/attempts/{id}/grade-override` | U own, T own | E5-S3 | Add a human grade |
| POST | `/attempts/{id}/regrade` | U own, T own, A | E5-S3 | Retry after `GRADING_FAILED`; `202` |

```json
// POST /practice-sessions
{ "courseId": "…", "mode": "SELF", "topicId": "…", "questionCount": 5 }
// POST /practice-assignments
{ "studentId": "…", "courseId": "…", "title": "Photosynthesis check", "questionIds": ["…"], "dueAt": null }
// Session
{ "id": "…", "studentId": "…", "courseId": "…", "mode": "SELF", "title": "Practice: Photosynthesis",
  "status": "IN_PROGRESS", "assignedBy": null, "dueAt": null, "createdAt": "…", "completedAt": null,
  "averageScore": null,
  "items": [ { "position": 1,
      "question": { "id": "…", "type": "WRITING", "difficulty": 3, "stem": "…", "options": null, "topicId": "…", "aiGenerated": true },
      "attempt": { "id": "…", "status": "DRAFT", "draftText": "", "answerText": null, "selectedOption": null,
                   "draftSavedAt": null, "submittedAt": null, "grade": null } } ] }
// session status: IN_PROGRESS | SUBMITTED | GRADED
// attempt status: DRAFT | GRADING | GRADED | GRADING_FAILED
// PUT /attempts/{id}/draft   -> 200 { "draftSavedAt": "…" }
{ "draftText": "Plants use sunlight to…", "selectedOption": null }
// POST /attempts/{id}/submit -> 202 Attempt
{ "answerText": "Plants use sunlight to…", "selectedOption": null }
// Grade (inside an attempt once graded)
{ "id": "…", "source": "AI", "score": 70, "feedback": "You explained the inputs well…",
  "perCriterion": [ { "criterion": "Names inputs", "points": 2, "maxPoints": 2, "comment": "…" } ],
  "misconceptions": ["Says oxygen is an input"], "gradedAt": "…", "graderName": null }
// grade source: AUTO (multiple choice) | AI | HUMAN
// POST /attempts/{id}/grade-override
{ "score": 85, "feedback": "The second paragraph deserves credit." }
```

Rules:
- `questionCount` is 1 to 10. `SELF` picks `APPROVED` questions of the topic the student has not answered yet; fewer than requested is fine, none is `409`.
- A draft can be saved only while the attempt is `DRAFT`; `draftText` is at most 10,000 characters.
- Submitting a multiple choice attempt returns it already `GRADED` (source `AUTO`, score 0 or 100). Written attempts return `GRADING`.
- `GET /attempts/{id}`: `grade` is the effective grade (the newest `HUMAN` grade, otherwise the `AI` or `AUTO` grade). Staff also get `grades`, the full history.
- Staff views of a session include `referenceAnswer` and `rubric` in each question. Student and parent views never do, and they show `correctOption` only after the attempt is graded.
- A session becomes `SUBMITTED` when every attempt is submitted and `GRADED` when every attempt is `GRADED`.

**SSE `/practice-sessions/{id}/events`** (`text/event-stream`):

```
event: attempt-graded
data: {"attemptId":"…","status":"GRADED","score":70}

event: session-graded
data: {"sessionId":"…","averageScore":76}
```

A comment line `: keep-alive` is sent every 20 seconds. The stream ends after `session-graded`.

## 8. Mastery and progress

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/students/{studentId}/mastery?courseId=` | S own, U own, T own, P own, A | E6-S1 | Mastery per topic |
| GET | `/students/{studentId}/progress?courseId=` | same | E6-S3 | Summary for one student |
| GET | `/courses/{courseId}/progress` | T own, A | E6-S3 | Class view |

```json
// mastery
[ { "topicId": "…", "topicName": "Photosynthesis", "mastery": 0.62, "band": "DEVELOPING", "attempts": 7, "updatedAt": "…" } ]
// band: BEGINNING (< 0.40) | DEVELOPING (0.40 to < 0.75) | SECURE (>= 0.75)
// student progress
{ "studentId": "…", "courseId": "…", "sessionsCompleted": 4, "attemptsGraded": 19, "averageScore": 71,
  "scoreByDay": [ { "date": "2026-11-12", "averageScore": 68, "attempts": 5 } ],
  "weakTopics": [ { "topicId": "…", "topicName": "Respiration", "mastery": 0.31 } ],
  "recentAttempts": [ { "attemptId": "…", "questionStem": "…", "topicName": "…", "score": 70, "gradedAt": "…" } ] }
// course progress
{ "courseId": "…", "students": 12,
  "topics": [ { "topicId": "…", "topicName": "…", "averageMastery": 0.58, "studentsBelowDeveloping": 3 } ],
  "studentSummaries": [ { "studentId": "…", "displayName": "…", "averageMastery": 0.61, "attemptsGraded": 19, "lastActiveAt": "…" } ] }
```

`scoreByDay` covers the last 14 days. `weakTopics` holds up to 3 topics with the lowest mastery among topics the student has attempted.

## 9. Tutor chat

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| POST | `/chat/conversations` | S | E7-S1 | Start a conversation for a course |
| GET | `/chat/conversations` | S own | E7-S1 | Own conversations, newest first |
| GET | `/chat/conversations/{id}/messages` | S own | E7-S1 | History |
| POST | `/chat/conversations/{id}/messages` | S own | E7-S1 | Send a message; the reply streams back as SSE |
| DELETE | `/chat/conversations/{id}` | S own | E7-S1 | Delete with its history |

```json
// POST /chat/conversations   { "courseId": "…" }  -> 201 { "id": "…", "courseId": "…", "title": "New conversation", "createdAt": "…" }
// POST /chat/conversations/{id}/messages   { "text": "Why are leaves green?" }
// GET messages -> [ { "role": "USER", "text": "…", "createdAt": "…" }, { "role": "ASSISTANT", "text": "…", "createdAt": "…" } ]
```

Reply stream (`text/event-stream`):

```
event: token
data: {"text":"Leaves are green because "}

event: done
data: {"sources":[{"lessonId":"…","lessonTitle":"How plants make food"}]}

event: error
data: {"code":"AI_UNAVAILABLE","message":"The tutor is not available right now"}
```

`text` is 1 to 2,000 characters. Errors known before streaming starts (validation, rate limit, not found) are normal JSON error responses.

## 10. Notifications

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/notifications?unread=` | all own | E8-S1 | Page, newest first |
| GET | `/notifications/unread-count` | all own | E8-S1 | `{ "count": 3 }` |
| POST | `/notifications/{id}/read` | all own | E8-S1 | Mark one read |
| POST | `/notifications/read-all` | all own | E8-S1 | Mark all read |
| GET | `/notification-preferences` | all own | E8-S2 | Own preferences |
| PUT | `/notification-preferences` | all own | E8-S2 | Update |

```json
// Notification
{ "id": "…", "type": "ATTEMPT_GRADED", "title": "Your answer was graded", "body": "You scored 70 on Photosynthesis.",
  "link": "/practice/…/results", "readAt": null, "createdAt": "…" }
// type: LESSON_PUBLISHED | PRACTICE_ASSIGNED | ATTEMPT_GRADED | SESSION_GRADED | QUESTIONS_GENERATED
// preferences
{ "emailEnabled": true, "mutedTypes": ["LESSON_PUBLISHED"] }
```

## 11. AI operations

| Method | Path | Roles | Story | Purpose |
|---|---|---|---|---|
| GET | `/ai/ping` | A | E0-S6, secured in E1-S1 | One tiny model call to prove the provider works |
| POST | `/ai/embedding` | A | E0-S6, secured in E1-S1 | Embed a text (max 20000 characters) to prove the embedding model works; used by `scripts/bench-llm.sh` |
| GET | `/ai/usage?from=&to=` | A | E9-S2 | Calls, tokens, latency and failures per feature |
| GET | `/ai/calls?feature=&outcome=` | A | E9-S2 | Page of `ai_call_log` rows |

```json
// ping
{ "provider": "ollama", "model": "llama3.2:3b", "reply": "pong", "latencyMs": 812 }
// embedding: request { "text": "..." }
{ "provider": "ollama", "model": "nomic-embed-text", "dimensions": 768, "latencyMs": 95 }
// usage
[ { "feature": "QUESTION_GEN", "calls": 12, "failures": 1, "promptTokens": 18230, "completionTokens": 6110, "p50LatencyMs": 21400, "p95LatencyMs": 38900 } ]
```

AI features: `PING`, `INDEXING`, `SEARCH`, `QUESTION_GEN`, `GRADING`, `CHAT`.

**Rate limit (E9-S2):** endpoints that start model work (`/question-generation-jobs`, `/attempts/{id}/submit` for written answers, `/chat/**/messages`, `/content-search`, `/ai/ping`) allow 20 calls per user per minute by default (`tutor.ai.rate-limit.per-minute`). Over the limit is `429 RATE_LIMITED`.

## 12. OpenAPI

springdoc serves `/v3/api-docs` and `/swagger-ui/index.html`. Every controller method has an `@Operation` summary and its error responses declared. `docs/openapi.yaml` is exported from the running app in E9-S4.
