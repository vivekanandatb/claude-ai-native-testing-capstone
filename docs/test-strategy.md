# Test Strategy

Status: proposal for approval. **No tests are implemented by this document.**
Inputs: `reports/repository-inspection.md`, `reports/baseline-results.md`, source files read directly, and behavior observed while running the baseline (Stage 4–5). Rules: `CLAUDE.md`.

Evidence labels used throughout: **[SRC]** read in source, **[RUN]** observed by running something, **[DOC]** stated only in a README/brief, **[NONE]** no evidence.

---

## 1. What is actually under test

Two separate code bases are in scope. They share no code.

| ID | Component | What it really is | Runnable here? |
|---|---|---|---|
| **P** | `payment-login-java-project/` (Java 17 library, JUnit 5, Maven) | `LoginService` + in-memory `UserRepository` with one synthetic account. No HTTP layer, no UI, no payment code. | Yes. `mvn test` → 24/24 pass [RUN] |
| **C-API** | `starter_code/backend` (Spring Boot 3.5.4, Java 21 target) | Course catalogue REST API: `/api/courses`, `/api/enrollments`, `/api/learners`. | Mock-based tests only. Live start fails, MySQL absent [RUN] |
| **C-UI** | `starter_code/frontend` (React 18 + Vite 5) | One page: "Course catalogue" with a level filter and a course list. | Builds and serves on :5173 [RUN]; API calls return 500 without backend |

## 2. Confirmed facts, assumptions, open questions, unsupported items

### 2.1 Confirmed facts (evidence-backed)
- F1. `LoginService.login(username, password)` returns boolean; null/blank → false; unknown user and wrong password both → false; constant-time, case-sensitive password compare. [SRC+RUN]
- F2. `UserRepository` holds exactly one synthetic account (`demo.user`) with a **plaintext** password. [SRC]
- F3. The catalog API has no authentication or authorization: `anyRequest().permitAll()`, CSRF disabled. [SRC]
- F4. `PUT /api/courses/{code}` validates: title required ≤200; description ≤2000; credits 5–60; capacity 1–500; instructorEmail valid email ≤120. Errors → 400 `VALIDATION_FAILED` with a `fields` map. [SRC]
- F5. `POST /api/courses/{code}/archive` requires `reason` (non-blank, ≤500). Archiving twice → 409 `COURSE_ALREADY_ARCHIVED`; updating an archived course → 409 `COURSE_ARCHIVED`; unknown code → 404 `COURSE_NOT_FOUND`; reactivating an active course → 409 `COURSE_ALREADY_ACTIVE`. [SRC; reactivate-conflict also RUN]
- F6. `POST /api/enrollments` → 201 on success; 409 `DUPLICATE_ENROLLMENT`, `SEAT_UNAVAILABLE`, `COURSE_NOT_OPEN`; 404 `LEARNER_NOT_FOUND`. Seat rule: reject when active enrolments ≥ capacity. [SRC]
- F7. `EnrollmentCreateRequest` has **no validation annotations** although the controller uses `@Valid`. [SRC]
- F8. The DB has no unique constraint on (course, learner) in migration V5; the duplicate guard lives only in `EnrollmentService`. [SRC]
- F9. `GET /api/courses/{code}` returns `instructorEmail`, `description`, `archiveReason`; the list endpoint returns only id, code, title, level, credits. [SRC]
- F10. `CatalogAuditLogger` writes the free-text archive `reason` straight into the log line; actor is the hard-coded string `"anonymous"`. [SRC]
- F11. Keyword search uses bound parameters in a native query (not string-concatenated), but `%` and `_` in the keyword are not escaped. [SRC]
- F12. The UI has one page, a level-filter `role="group"` with `aria-label="Filter by level"`, native `<button>` elements, and loading/error/empty states with fixed texts. The selected filter is shown only by a CSS class (`chip--on`); there is no `aria-pressed`/`aria-current`. [SRC]
- F13. The UI never calls the keyword search or detail endpoints; `/login` and `/payment` return the SPA shell, not pages. [SRC+RUN]
- F14. Demo seed course `LMS9001` carries a description with instructions aimed at AI assistants; seed also has duplicate ACTIVE enrolments and an over-capacity course. [SRC]
- F15. Existing tests: catalog 10 (4 controller, 2 mapper, 4 parser), all pass; payment 24, all pass. No frontend tests; no frontend test runner. [RUN]

### 2.2 Assumptions (not verified; tests must not depend on them)
- A1. The intended capstone target is the payment/login project (user choice), with the catalog as the runnable web app.
- A2. "Username" is not an email: the only fixture username (`demo.user`) is not email-shaped. Email-format rules for login are therefore **not** asserted.
- A3. The catalog's open API is a training-starter artifact and not intended as a production security posture.
- A4. Java 25 EA behaves like Java 21/17 for these tests (consistent with all runs so far).

### 2.3 Open questions (answers change scope)
- Q1. Is a **payment** module expected? None exists in either code base.
- Q2. Should login hashing/lockout be implemented (F2), or is plaintext synthetic-only acceptable?
- Q3. Is a **session / logout / protected page** layer expected? None exists.
- Q4. Should the catalog get authentication (F3), or should tests only characterize the current open behavior?
- Q5. May a UI test dependency be added (Playwright, or Vitest + Testing Library)? Needed for any C-UI automation.
- Q6. What should `POST /api/enrollments` do with missing `courseCode`/`learnerRef` (F7)? Real behavior is unknown without a DB-backed run.
- Q7. Are the committed dev credentials in `application.yml`, `db-setup/01-create-users.sql`, `.vscode/mcp.json` to be fixed or left?
- Q8. Is RAG/LLM functionality in scope? None exists in the catalog or payment project.

### 2.4 Unsupported scenarios (no code to test; no tests will be written unless the feature is approved)
| Capability | Why unsupported |
|---|---|
| Logout, session invalidation, protected page after logout | No session, token, cookie, or protected route exists in any component [SRC] |
| Payment field validation, success, decline, timeout, idempotency | No payment code, route, gateway reference, or UI [SRC] |
| Login UI (form fields, labels, error messages) | No login page in C-UI; P has no UI [SRC+RUN] |
| Email-format validation on login | No such rule exists (A2) |
| Real-DB behavior (search, duplicate rows, capacity with real data, migrations) | MySQL unavailable; user chose mock-only (Stage 4 decision) |
| RAG retrieval/answer evaluation | No retrieval or LLM component exists in P, C-API or C-UI |

The matrix lists these as `UNSUP-*` placeholders so the gap is visible. They are not counted as automation candidates.

## 3. Risk model

Priority = likelihood × impact, scored from evidence, not opinion.

| Priority | Meaning | Examples |
|---|---|---|
| **P0** | Authentication/authorization or data-integrity failure that is already present or one regression away | Open write endpoints (F3); enrollment capacity/duplicate integrity (F6–F8) |
| **P1** | Wrong results, information exposure, or a gate that silently stops protecting | Login rejection paths; PII on public GET (F9); log injection (F10) |
| **P2** | Functional regressions in stable logic | Validation boundaries, parser, mapper |
| **P3** | UX/accessibility polish, cosmetic | Unused `formatCredits`; no `aria-pressed` (F12) |

## 4. Test layers and tooling (only what exists or needs approval)

| Layer | Tool | Available now? | DB needed | Notes |
|---|---|---|---|---|
| Unit (P) | JUnit 5 + parameterized | Yes | No | |
| Unit (C-API services) | JUnit 5 + Mockito + AssertJ | Yes (spring-boot-starter-test) | No | Mock repositories |
| Slice/API (C-API controllers) | `@WebMvcTest` + MockMvc + `@MockitoBean` | Yes (pattern already in `CourseControllerTest`) | No | Verified APIs only (F4–F6) |
| Log assertion | `OutputCaptureExtension` (spring-boot-test) | Yes | No | |
| Static/repo checks | JUnit file-reading test or script | Yes | No | Secret-pattern scan |
| UI component/E2E | Vitest + Testing Library **or** Playwright | **No. Needs approval (Q5)** | No (API stubbed) | Use role/label/text locators (CLAUDE.md #13); no fixed sleeps (#14) |
| AI-evaluated | Rubric review of model output | Not applicable now | n/a | Only if a RAG/LLM component is approved |

Determinism rules: synthetic data only; no network; no clock/UUID dependence unless injected; each test builds its own fixtures; no shared mutable state; no sleeps.

## 5. Strategy by theme

1. **Login (P).** Already strongest: 24 deterministic unit tests. Remaining: extreme-length and Unicode inputs, repeated-call stability, and documenting the known gaps (no lockout, plaintext storage) as explicit non-goals until Q2.
2. **Catalog API (C-API).** Highest value per effort: MockMvc tests of verified error contracts (F4–F6), plus Mockito unit tests of `EnrollmentService` and `CourseService` business rules (capacity boundary, duplicate guard, inactive course, keyword trimming).
3. **Authorization abuse (C-API).** Existing controller tests use `addFilters = false`, so `SecurityConfig` is never exercised. Add a characterization test with the real security filter chain that records today's behavior (anonymous writes succeed). It must be labeled *characterization of a known defect*, not an endorsement, and flipped once Q4 is decided. Do not "fix" the assertion to look green (CLAUDE.md #10).
4. **Sensitive information.** Assert list vs. detail field exposure (F9), that error bodies do not leak internals, that login never logs credentials, and run a secret-pattern scan over the payment project. The same scan over `starter_code` would currently fail (Q7), so it is held as a known-failing check pending a decision.
5. **Logging safety.** Capture the audit log and show that a newline-bearing archive `reason` is written verbatim (F10), as a characterization/bug-candidate test.
6. **UI.** Blocked on Q5. If approved: stub `fetch`, assert loading/error/empty/list states, filter-button behavior and request URL, keyboard activation, and the missing selected-state semantics (F12).
7. **Regression.** Parser and mapper edge cases; cross-checks that summary DTO ⊂ detail DTO; CI so the suite runs on every PR (currently CI runs no tests).
8. **Payment / session / RAG.** Out of scope until Q1/Q3/Q8 are answered. A conditional design is sketched in `test-matrix.md` §9 and clearly marked unsupported.

## 6. Automation order (recommended)

1. P: boundary/stability additions to `LoginServiceTest` (cheap, same file, no new dependency).
2. C-API: error-contract and validation-boundary MockMvc tests (course update/archive; enrollment error mapping).
3. C-API: `EnrollmentService` unit tests (capacity and duplicate rules).
4. C-API: security characterization test (real filter chain).
5. C-API: exposure tests (list vs. detail) + audit-log capture.
6. P/C: secret-scan test for the payment project.
7. Parser/mapper regression additions.
8. CI workflow running `mvn test` for both Maven projects and `npm run build` for the frontend.
9. C-UI tests, only after Q5.

## 7. Entry / exit criteria

- Entry: baseline report reviewed; open questions Q1–Q8 triaged; scope approved by the user.
- Per scenario: automated only if deterministic; every test must be seen failing under a deliberate mutation (done for login in Stage 5) or have a clear failure mode.
- Exit (final suite): ≥ 8 stable automated tests beyond the current baselines, each run at least twice with identical results, results recorded with command and output in an evidence file; remaining risks listed.
- No test is reported as passing unless run and its output inspected (CLAUDE.md #9).

## 8. Risks to the strategy itself
- The brief (login + payment) does not match the catalog app; a large share of requested matrix areas are unsupported (§2.4).
- Mock-only decision means DB-level integrity (F8, F14) is characterized by reading source, not by observed data.
- Java 25 EA is a pre-release runtime (A4).
- Deleting/replacing `starter_code/frontend/node_modules` was done in Stage 5; dependency advisories from `npm audit` remain uninvestigated.
