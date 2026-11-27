# tutor-web

The Angular 21 Nx workspace for the AI Tutor Platform. Node 22 and npm 10.

| Project         | Path                 | Contents                                                                                                                           |
| --------------- | -------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `tutor-app`     | `apps/tutor-app`     | The app: Material theme, shell (top bar and side navigation), lazy feature routes under `src/app/features/`                        |
| `api`           | `libs/api`           | `ApiError`, `Page<T>`, `apiErrorInterceptor`, `SseClient`, typed API clients (`HealthApi`, `UserApi`, `RelationshipApi`)           |
| `ui`            | `libs/ui`            | `tp-loading`, `tp-empty-state`, `tp-error-banner`, `tp-page-header`                                                                |
| `auth`          | `libs/auth`          | OpenID Connect sign-in (`provideTutorAuth`, `AuthService`, `authInterceptor`, `roleGuard`)                                         |
| `tutor-app-e2e` | `apps/tutor-app-e2e` | Cypress + Cucumber scenarios (`src/features/*.feature`) against the running stack                                                  |
| `e2e-support`   | `libs/e2e-support`   | `tokenFor(username)` (Keycloak `tutor-cli` password grant), `apiRequest` / `apiRequestAs`, `withRunPrefix`, `cy.loginAs(username)` |

Lint boundaries: the app imports only libraries, never `e2e-support`; `api` and `e2e-support` import nothing; `ui` and `auth` may import `api`; `tutor-app-e2e` imports only `e2e-support`.

## Commands

```bash
npm ci                                   # install exactly what package-lock.json pins
npx nx serve tutor-app                   # http://localhost:4310; /api and /actuator proxy to tutor-api on :9190
npx nx run-many -t lint test build       # every gate: zero lint warnings, coverage thresholds, production build
npx nx test api                          # one project's unit tests (Vitest) with coverage
npx nx lint tutor-app
npx nx build tutor-app                   # production build into dist/apps/tutor-app
npx nx format:check                      # Prettier; npx nx format:write to fix
npx nx graph                             # project graph
npx nx e2e tutor-app-e2e                 # e2e against the running stack (./dev.sh on fake-ai); CYPRESS_tags=@smoke for one tag
```

Coverage reports go to `coverage/<project>/`. Every project fails its `test` target below 90% statements and lines or 80% branches (test strategy §3).

Signing in uses the authorization code flow with PKCE against Keycloak (`environment.ts` holds the authority and client). Tokens stay in session storage and are sent only on `/api/` calls. The shell shows the navigation of the signed-in role (only items whose route exists, otherwise Home), `/forbidden` is the page for a role that may not open a route, and `/admin/users` is the admin's users screen with guardian-link and tutor-assignment dialogs.

The home page reads `/actuator/health` and shows "API: UP" when tutor-api runs, or an error banner with a retry button when it does not. The colour scheme follows the operating system (light or dark) through the Material theme.

`npx nx e2e tutor-app-e2e` needs the whole stack (`./dev.sh` from the repository root) with tutor-api on `fake-ai`: the AI scenario first checks `/actuator/info` and fails, without calling the model, when another profile is active. Run `AI_PROFILE=fake-ai ./dev.sh` when Ollama is running. The URLs default to the dev ports and can be changed with `CYPRESS_apiUrl`, `CYPRESS_keycloakUrl` and `CYPRESS_webUrl`. Failure screenshots go to `dist/cypress/apps/tutor-app-e2e/`, the Cucumber report to `apps/tutor-app-e2e/reports/`.

`cy.loginAs(username)` signs in through the real Keycloak page inside `cy.session`; the `@ui @smoke` sign-in feature and the `@ui` admin users feature need the stack on `fake-ai` and the realm users from `infra/keycloak`. Run one feature with `npx nx e2e tutor-app-e2e --spec src/features/sign-in.feature` or select by tag with `CYPRESS_tags='@ui'`.
