# Design system and screens

One Angular 21 app, `tutor-app`, serves all five roles. The navigation and the home page depend on the signed-in user's role.

## 1. Rules

- Standalone components, signals for state, `inject()` for dependencies, the built-in control flow (`@if`, `@for`). No NgModules.
- Angular Material 3 with one theme (primary indigo, tertiary teal), light and dark through `color-scheme`. No custom colours in component styles; use the Material system tokens.
- Every feature is a lazy-loaded route under `apps/tutor-app/src/app/features/<feature>/`.
- HTTP goes through typed clients in `libs/api` (`CourseApi`, `LessonApi`, ...). Components never call `HttpClient` directly. Models in `libs/api` mirror the API design exactly, including fields that can be `null`.
- Every page that loads data shows three states with the shared components in `libs/ui`: `<tp-loading>`, `<tp-empty-state>` and `<tp-error-banner>` (which shows the API error `message` and a retry button).
- Forms are typed reactive forms. Submit buttons are disabled while a request runs.
- Accessibility: every control has a label, focus is visible, and pages work with the keyboard alone.
- Selectors use the `tp-` prefix. Tests find elements by role or label, or by `data-testid` when neither exists.

## 2. Shell

Top bar: app name, notification bell with unread count, user menu (display name, role, sign out). Side navigation by role:

| Role | Items |
|---|---|
| ADMIN | Users, Lesson review, Courses, AI usage |
| TEACHER | Courses, Question bank, Class progress |
| TUTOR | My students, Question bank |
| STUDENT | My courses, Practice, Progress, Tutor chat |
| PARENT | My children |

## 3. Routes

| Route | Roles | Screen | Story |
|---|---|---|---|
| `/` | all | Redirects to the role's first navigation item | E1-S4 |
| `/forbidden` | all | "You don't have access" page | E1-S4 |
| `/admin/users` | A | Users table with role filter; guardian and tutor link dialogs | E1-S4 |
| `/courses` | A, T, S | Course cards | E2-S3 |
| `/courses/:courseId` | A, T, S | Tabs: Lessons, Topics, Students (staff only) | E2-S3 |
| `/courses/:courseId/lessons/new`, `/lessons/:lessonId/edit` | T | Lesson editor: title, topic, markdown with preview, submit for review | E2-S3 |
| `/lessons/:lessonId` | all | Lesson reader (rendered markdown); staff see status, index status and actions | E2-S3, E3-S3 |
| `/admin/review` | A | Lessons in review; publish or request changes | E2-S3 |
| `/questions` | T, U | Question bank with filters; teacher review actions | E4-S4 |
| `/questions/generate` | T | Generation form, job progress, results to review | E4-S4 |
| `/practice` | S | Sessions list, "Start practice" dialog | E5-S4 |
| `/practice/:sessionId` | S | Writing board | E5-S4 |
| `/practice/:sessionId/results` | S, U, T, P | Scores, feedback per question, rubric table; override for staff | E5-S4 |
| `/progress` | S | Own mastery and progress | E6-S4 |
| `/students` | U, P | Assigned students or children as cards | E6-S4 |
| `/students/:studentId` | U, P, T | One student's progress and sessions; tutors can assign practice | E6-S4 |
| `/courses/:courseId/progress` | T, A | Class progress | E6-S4 |
| `/chat`, `/chat/:conversationId` | S | Tutor chat | E7-S4 |
| `/notifications` | all | Inbox and preferences | E8-S3 |
| `/admin/ai-usage` | A | AI usage table | E9-S2 |

## 4. Key screens

**Writing board (`/practice/:sessionId`)**
- Left: question list with a state chip per question (not started, draft, submitted, graded).
- Centre: the question stem; radio buttons for multiple choice, or a large text area with a word count for written answers.
- Drafts save automatically 1.5 seconds after typing stops and on leaving a question; a "Saved 12:41" or "Saving…" indicator sits under the text area. A failed save shows "Not saved. Retry".
- "Submit answer" per question and "Submit all" for the session. After submit the answer is read-only and shows "Grading…" until the SSE event arrives, then the score and feedback.

**Tutor chat**
- Conversation list on the left, messages on the right, input at the bottom.
- The assistant's reply appears token by token; the send button becomes "Stop" while streaming.
- Sources from the `done` event render as lesson links under the reply.

**Question review**
- Card per question: stem, options with the correct one marked, reference answer, rubric table, "AI generated" chip, source lesson link.
- Approve, Edit, Reject (asks for a reason). Keyboard shortcuts `A` and `R` on the focused card.

## 5. Streaming

`EventSource` cannot send an `Authorization` header, so `libs/api` has `SseClient`, built on `fetch` and a `ReadableStream` reader. It parses `event:` and `data:` lines, exposes an `Observable`, and aborts the request on unsubscribe.
