# Module design

## Repository layout

```
tutor-api/                         Spring Boot backend (Maven wrapper)
  src/main/java/io/github/samarthshubham/tutor/
    common/      error model, paging, clock, shared value types
    identity/    current user, roles, app_user, guardian and tutor links
    course/      school, course, topic, enrollment
    cms/         lesson authoring and publishing workflow
    practice/    question bank, generation jobs, sessions, attempts, grades
    mastery/     topic mastery, adaptive selection, progress views, tutor chat conversations
    ai/          everything that talks to a model
    notify/      in-app notifications and email
  src/main/resources/
    application.yml, application-{dev,ollama,cloud,fake-ai,test}.yml
    db/migration/V<n>__<name>.sql
    prompts/<name>.v<n>.st
tutor-web/                         Angular 21 Nx workspace
  apps/tutor-app/                  the single web app for all five roles
  apps/tutor-app-e2e/              Cypress + Cucumber
  apps/seed-demo-data/             Cucumber-driven idempotent demo seed
  libs/auth/                       OIDC login, token interceptor, role guards
  libs/api/                        typed API clients and models
  libs/ui/                         shared components (page header, empty state, error banner)
  libs/e2e-support/                shared Cucumber steps and API helpers
infra/                             docker-compose.yml, keycloak/, postgres/, prometheus/, grafana/
docs/                              this folder
```

## Inside a module

Every backend module has the same sub-packages. Create only the ones the module needs.

| Package | Holds | Rule |
|---|---|---|
| `<module>.api` | Interfaces, records and events other modules may use | The only package another module may import |
| `<module>.web` | `@RestController` classes, request and response records | No repositories or entities here; controllers call services |
| `<module>.service` | Use cases, `@Transactional` boundaries | Owns business rules and authorization checks that need data |
| `<module>.domain` | JPA entities, enums, repositories | Never returned from a controller |
| `<module>.config` | `@Configuration` and `@ConfigurationProperties` | |

Request and response types are Java records with Bean Validation annotations. Entities are mapped to records in the service or a small mapper class.

## Which module may use which

| Module | May import `api` of |
|---|---|
| `common` | nothing |
| `identity` | `common` |
| `course` | `common`, `identity` |
| `cms` | `common`, `identity`, `course`, `ai` |
| `practice` | `common`, `identity`, `course`, `cms`, `ai` |
| `mastery` | `common`, `identity`, `course`, `practice`, `ai` |
| `notify` | `common`, `identity`, `course`, `cms`, `practice` |
| `ai` | `common` only |

`ai` knows nothing about courses, lessons or students. When the model needs student data (tutor chat tools), `ai.api` declares a provider interface (`StudentContextProvider`) and `mastery` implements it. This keeps the dependency pointing into `ai.api`.

`ModuleBoundaryTest` (ArchUnit) enforces this table and the rule that `org.springframework.ai..` is imported only under `tutor.ai..`.

## Events

Modules tell each other that something happened with Spring application events. Events are records in the publishing module's `api` package. Listeners that do slow or failure-prone work use `@TransactionalEventListener(phase = AFTER_COMMIT)` together with `@Async`.

| Event (record) | Published by | Fields | Listeners |
|---|---|---|---|
| `cms.api.LessonPublished` | `cms` on publish | `lessonId, version, courseId, topicId` | `cms` indexing listener (calls `ai.api.ContentIndexer`), `notify` |
| `cms.api.LessonArchived` | `cms` on archive | `lessonId` | `cms` indexing listener (removes chunks) |
| `practice.api.QuestionsGenerated` | `practice` when a generation job ends | `jobId, requestedBy, createdCount, failed` | `notify` |
| `practice.api.PracticeAssigned` | `practice` when a tutor or teacher assigns a session | `sessionId, studentId, assignedBy` | `notify` |
| `practice.api.AttemptSubmitted` | `practice` on submit | `attemptId, sessionId, studentId` | `practice` grading listener |
| `practice.api.AttemptGraded` | `practice` when a grade is stored | `attemptId, sessionId, studentId, topicId, score, source` | `mastery`, `notify`, `practice` SSE publisher |

## Cross-cutting rules

- **Time:** inject `java.time.Clock` (bean in `common.config`); never call `Instant.now()` directly. Timestamps are `Instant`, stored as `timestamptz`, sent as ISO-8601 UTC.
- **Ids:** `UUID`, generated in the application.
- **Current user:** `identity.api.CurrentUser` (`id, keycloakId, role, schoolId`) is resolved from the JWT by `identity.api.CurrentUserProvider`. Controllers take it as a method argument.
- **Authorization:** role checks with `@PreAuthorize("hasRole('TEACHER')")` on controller methods; data ownership checks in services through `identity.api.AccessPolicy` (for example `canSeeStudent(currentUser, studentId)`). A failed ownership check is `404`, not `403`, so ids cannot be probed.
- **Errors:** one `@RestControllerAdvice` in `common.web`. Body is `ErrorResponse` (see the API design). Services throw `common.api` exceptions: `NotFoundException`, `ConflictException`, `ValidationException`, `ForbiddenException`, `AiUnavailableException`, `RateLimitedException`.
- **Paging:** `common.api.PageResponse<T>` built from a Spring Data `Page`.
- **Logging:** log ids, never names, emails, answer text or prompt text at `INFO`.
- **Formatting:** Spotless with Google Java Format; Prettier for the web workspace.
