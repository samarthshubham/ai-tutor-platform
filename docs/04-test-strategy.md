# Test strategy

No test ever calls a real model. Tests and CI run with the `fake-ai` profile (see the AI design, §4). Real models are used only by the opt-in `eval` profile and by manual demos.

## 1. Levels

| Level | What it proves | How | Naming |
|---|---|---|---|
| **unit** | One class's logic | JUnit 5 + Mockito, no Spring context | `<Class>Test` |
| **web slice** | Request mapping, validation, error bodies, role checks | `@WebMvcTest` with the security config and mocked services | `<Controller>Test` |
| **authorization matrix** | Every endpoint against all five roles and anonymous | Parameterized web slice test, one row per role with the expected status | `<Controller>AuthorizationTest` |
| **integration** | Repositories, Flyway, transactions, events, pgvector | `@SpringBootTest` + Testcontainers `pgvector/pgvector:pg16`, profiles `test,fake-ai` | `<Feature>IT` |
| **architecture** | Module boundaries and the Spring AI import rule | ArchUnit | `ModuleBoundaryTest` |
| **frontend unit** | Components, services, guards | Vitest + Angular Testing Library style queries | `*.spec.ts` |
| **e2e** | A flow through the real stack | Cypress + Cucumber against `dev.sh` with `fake-ai` | `*.feature`, tags below |
| **eval** | Quality of real model output | `./mvnw verify -Peval`, never in CI | `*Eval` |

Integration tests share one container through a base class `IntegrationTest` in `tutor-api/src/test/java/.../support/`. Tokens for tests come from `TestJwt` (builds a JWT with the realm role claim; no Keycloak in backend tests).

## 2. e2e tags

| Tag | Meaning |
|---|---|
| `@api` | Calls the API directly with a token |
| `@ui` | Drives the browser |
| `@journey` | Crosses roles end to end (roadmap §3) |
| `@smoke` | Minimal check that the stack and login work; runs on every pull request that touches code |

e2e scenarios create their own data through the API with a unique run prefix and remove it afterwards. They never depend on the demo seed.

## 3. Gates

| Gate | Rule |
|---|---|
| Backend coverage | JaCoCo line coverage at least 90%, enforced by `./mvnw verify`. Excluded: the application main class, `*.config.*` and generated code only |
| Frontend coverage | Vitest: at least 90% statements and lines, 80% branches, per project |
| Formatting | `./mvnw spotless:check`; `npx nx format:check` |
| Lint | `npx nx lint` with zero warnings |
| Build | `./mvnw verify`; `npx nx build tutor-app` |

Gates are never lowered and tests are never disabled to make a change pass.

## 4. What every story delivers

- Backend endpoint: unit tests for the service, a web slice test, a row per role in the authorization matrix, and an integration test for anything that touches the database or an event.
- AI flow: unit tests with `FakeChatModel` covering the valid path, invalid output and a timeout.
- Screen: Vitest specs for each component and service, with loading, empty and error states covered; one `@ui` scenario for the main path.
- Bug fix: a test that fails before the fix.

## 5. Running

```bash
cd tutor-api && ./mvnw verify                     # unit, slice, integration, architecture, coverage gate
cd tutor-web && npx nx run-many -t lint test build
cd tutor-web && npx nx e2e tutor-app-e2e          # needs ./dev.sh running on fake-ai (AI_PROFILE=fake-ai when Ollama is up)
```
