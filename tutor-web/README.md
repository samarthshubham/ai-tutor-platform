# tutor-web

The Angular 21 Nx workspace for the AI Tutor Platform. Node 22 and npm 10.

| Project | Path | Contents |
|---|---|---|
| `tutor-app` | `apps/tutor-app` | The app: Material theme, shell (top bar and side navigation), lazy feature routes under `src/app/features/` |
| `api` | `libs/api` | `ApiError`, `Page<T>`, `apiErrorInterceptor`, `SseClient`, typed API clients (`HealthApi`) |
| `ui` | `libs/ui` | `tp-loading`, `tp-empty-state`, `tp-error-banner`, `tp-page-header` |
| `auth` | `libs/auth` | Roles; sign-in and guards arrive with E1-S4 |

Lint boundaries: the app imports only libraries; `api` imports nothing; `ui` and `auth` may import `api`.

## Commands

```bash
npm ci                                   # install exactly what package-lock.json pins
npx nx serve tutor-app                   # http://localhost:4300; /api and /actuator proxy to tutor-api on :9190
npx nx run-many -t lint test build       # every gate: zero lint warnings, coverage thresholds, production build
npx nx test api                          # one project's unit tests (Vitest) with coverage
npx nx lint tutor-app
npx nx build tutor-app                   # production build into dist/apps/tutor-app
npx nx format:check                      # Prettier; npx nx format:write to fix
npx nx graph                             # project graph
```

Coverage reports go to `coverage/<project>/`. Every project fails its `test` target below 90% statements and lines or 80% branches (test strategy §3).

The home page reads `/actuator/health` and shows "API: UP" when tutor-api runs, or an error banner with a retry button when it does not. The colour scheme follows the operating system (light or dark) through the Material theme.
