# Requirements Traceability

Maps each requirement or observed behavior to its source evidence, the matrix scenarios that cover it, and current status.
No formal requirements document exists in the repository. Two kinds of source are used and kept apart:

- **OBS-n**: *observed behavior* confirmed from source code or a run. This is what tests can assert.
- **BRF-n**: *capstone brief expectation* stated by the user/brief (login and payment application). It is **not** confirmed by repository evidence unless it maps to an OBS item.

Status: `Covered` (automated and passing) · `Partial` · `Planned` (deterministic, not started) · `Blocked(Qn)` · `Unsupported`.
Open questions Q1–Q8 are defined in `docs/test-strategy.md` §2.3.

## 1. Brief expectations vs. repository reality

| ID | Brief expectation | Repository evidence | Matrix scenarios | Status |
|---|---|---|---|---|
| BRF-1 | Valid login works | `LoginService.login` implemented (Stage 5) | LOGIN-01 | Covered |
| BRF-2 | Invalid password rejected | same | LOGIN-02, 06, 07 | Covered |
| BRF-3 | Blank username/password handled | same | LOGIN-04 | Covered |
| BRF-4 | Malformed username/email handled | No email rule; near-miss/injection strings tested | LOGIN-05, LOGIN-08 | Partial (email format unsupported, A2) |
| BRF-5 | Logout and session invalidation | No sessions | UNSUP-SES-01, 03 | Unsupported (Q3) |
| BRF-6 | Protected page after logout | No protected page | UNSUP-SES-02 | Unsupported (Q3) |
| BRF-7 | Payment validation / success / decline / timeout / idempotency | No payment code | UNSUP-PAY-01…06 | Unsupported (Q1) |
| BRF-8 | Boundary inputs | Applies to login and catalog DTOs | LOGIN-08, CAT-06, CAT-07, ENR-03 | Partial |
| BRF-9 | Auth/authz abuse cases | Catalog fully open; login has no lockout | SEC-01, SEC-02, LOGIN-09 | Planned / Blocked(Q2, Q4) |
| BRF-10 | Sensitive-information exposure | PII on detail GET, log injection, plaintext password, committed credentials | SEC-03…08, LOGIN-11 | Planned / Blocked(Q2, Q7) |
| BRF-11 | Keyboard/accessibility smoke | One UI page with filter buttons | UI-07, UI-08 | Blocked(Q5) |
| BRF-12 | RAG safety and evaluation | No RAG/LLM; only an untrusted-content fixture | RAG-01, RAG-02 (fixture level); RAG-03…05 | Partial / Unsupported (Q8) |
| BRF-13 | Regression, unit, API, UI opportunities | See sections below | REG-*, SVC-*, CAT-*, ENR-*, UI-* | Planned |

## 2. Observed behavior (OBS) → evidence → tests

### 2.1 Login (P)

| ID | Observed behavior | Source evidence | Scenarios | Tests today | Status |
|---|---|---|---|---|---|
| OBS-L1 | Valid user + password → true | `payment-login-java-project/src/main/java/com/technova/payment/auth/LoginService.java`, `login()` | LOGIN-01 | `loginWithValidCredentialsShouldSucceed` | Covered |
| OBS-L2 | Wrong password / unknown user → false, indistinguishable | same | LOGIN-02, 03, 07 | 3 tests | Covered |
| OBS-L3 | Null/blank inputs → false, no exception | same (`isBlank`) | LOGIN-04 | parameterized tests | Covered |
| OBS-L4 | Exact, case-sensitive matching; no trimming | same (`findByUsername`, `MessageDigest.isEqual`) | LOGIN-05, 06 | 4 tests | Covered |
| OBS-L5 | Null repository rejected | constructor | LOGIN-10 | `constructorRejectsNullRepository` | Covered |
| OBS-L6 | Plaintext password in fixture | `UserRepository.java`, `User.java` | LOGIN-11 | none | Blocked(Q2) |
| OBS-L7 | No lockout / rate limit | absence in `LoginService` | LOGIN-09 | none | Blocked(Q2) |
| OBS-L8 | No logging in login path | absence of logger usage | SEC-06 | none | Planned |

### 2.2 Catalog API (C-API)

Base path `starter_code/backend/src/main/java/com/globallearning/lms/catalog/`.

| ID | Observed behavior | Source evidence | Scenarios | Tests today | Status |
|---|---|---|---|---|---|
| OBS-C1 | List active courses (summary fields) | `web/CourseController#list`, `web/mapper/CourseMapper#toSummary` | CAT-01, SEC-03, REG-04 | `listsActiveCourses`, mapper tests | Covered / Partial |
| OBS-C2 | Detail by code; 404 when missing | `CourseController#getByCode`, `CatalogExceptionHandler#handleNotFound` | CAT-02, CAT-05 | `returnsCourseDetailByCode` (404 untested) | Partial |
| OBS-C3 | Update validation rules | `web/dto/CourseUpdateRequest.java` (`@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`) | CAT-06 | none | Planned |
| OBS-C4 | Archive requires reason ≤500; double archive → 409; update archived → 409 | `web/dto/ArchiveRequest.java`, `service/CourseService`, `CatalogExceptionHandler` | CAT-07, CAT-08 | none | Planned |
| OBS-C5 | Reactivate; already-active → 409 | `CourseService#reactivate` | CAT-03, CAT-04 | 2 tests | Covered |
| OBS-C6 | Deactivate has no open-enrolment guard (TODO) | `CourseService#deactivate` comment | CAT-10 | none | Planned (characterization) |
| OBS-C7 | Malformed JSON / bad enum / type errors | not observed | CAT-09 | none | Planned; behavior must be observed first |
| OBS-C8 | Keyword/level search path selection and trimming | `CourseService#listActive` | SVC-01 | none | Planned |
| OBS-C9 | Keyword search uses bound parameters; wildcards unescaped | `repository/CourseRepository` | SEC-09, SEC-10 | none | Blocked (needs DB) |

### 2.3 Enrollments (C-API)

| ID | Observed behavior | Source evidence | Scenarios | Tests today | Status |
|---|---|---|---|---|---|
| OBS-E1 | POST returns 201 with enrollment | `web/EnrollmentController#create` | ENR-01 | none | Planned |
| OBS-E2 | Error mapping 409/404 | `web/EnrollmentExceptionHandler` | ENR-02 | none | Planned |
| OBS-E3 | Rules: course exists → active → learner exists → no duplicate ACTIVE → seats < capacity | `service/EnrollmentService#enroll` | ENR-03, 04, 05, 08 | none | Planned |
| OBS-E4 | `EnrollmentCreateRequest` has no validation | `web/dto/EnrollmentCreateRequest.java` | ENR-07 | none | Blocked(Q6) |
| OBS-E5 | No DB unique constraint for (course, learner) | `db/migration/V5__create_learners_and_enrollments.sql` | ENR-06 | none | Blocked (needs DB) |
| OBS-E6 | Learner enrollment history by ref | `web/LearnerController` | ENR-09 | none | Planned |
| OBS-E7 | Demo seed deliberately contains duplicates/over-capacity | `db/demo/V90__demo_data.sql` header comments | ENR-06 | none | Informational |

### 2.4 Security and exposure

| ID | Observed behavior | Source evidence | Scenarios | Tests today | Status |
|---|---|---|---|---|---|
| OBS-S1 | All requests permitted, CSRF disabled | `config/SecurityConfig.java` | SEC-01, SEC-02 | none (existing tests bypass filters) | Planned / Blocked(Q4) |
| OBS-S2 | Detail endpoint exposes email, description, archive reason | `CourseMapper#toResponse` | SEC-03 | none | Planned |
| OBS-S3 | Audit log writes free-text reason; actor hard-coded `"anonymous"` | `audit/CatalogAuditLogger.java`, `CourseService` | SEC-05 | none | Planned |
| OBS-S4 | Committed dev credentials | `application.yml`, `db-setup/01-create-users.sql`, `.vscode/mcp.json` (values deliberately not reproduced) | SEC-08 | none | Blocked(Q7) |
| OBS-S5 | Unpinned `npx -y` MCP server with DB access | `.vscode/mcp.json` | none (config risk, not testable by unit test) | none | Reported in inspection |

### 2.5 Frontend (C-UI)

| ID | Observed behavior | Source evidence | Scenarios | Tests today | Status |
|---|---|---|---|---|---|
| OBS-U1 | Page title "Global Learning — Course catalogue", `#root`, heading "Course catalogue" | `frontend/index.html`, `src/App.jsx`; RUN: served at :5173 | UI-01 | none | Blocked(Q5) |
| OBS-U2 | Loading / error / empty states with fixed text | `src/components/CourseList.jsx` | UI-02…04 | none | Blocked(Q5) |
| OBS-U3 | Filter buttons call `GET /api/courses?level=…` | `LevelFilter.jsx`, `api/catalog.js` | UI-05 | none | Blocked(Q5) |
| OBS-U4 | Stale-response cancellation | `App.jsx` `cancelled` flag | UI-06 | none | Blocked(Q5) |
| OBS-U5 | Selected filter shown by CSS class only | `LevelFilter.jsx` | UI-08 | none | Blocked(Q5) |
| OBS-U6 | `/login`, `/payment` serve SPA shell, not pages | RUN (HTTP 200 text/html), no router in `src/` | UNSUP-SES-02, UNSUP-PAY-01 | n/a | Informational |
| OBS-U7 | Frontend build passes after `npm ci` | RUN (Stage 5) | UI-10 | none automated | Planned (CI) |
| OBS-U8 | Cards never render description | `CourseCard.jsx` | RAG-02 | none | Blocked(Q5) |

### 2.6 Regression and infrastructure

| ID | Observed behavior | Source evidence | Scenarios | Tests today | Status |
|---|---|---|---|---|---|
| OBS-R1 | Level/faculty derived from course code | `service/LegacyCourseCodeParser.java` | REG-01…03 | 4 parser tests | Partial |
| OBS-R2 | Existing CI only sets up Python | `.github/workflows/ci.yml` | REG-05 | none | Planned |
| OBS-R3 | Docs say H2 / Java 17, config says MySQL / Java 21 | `README.md`, `AGENTS.md` vs `application.yml`, `pom.xml` | REG-06 | none | Open |
| OBS-R4 | Untrusted partner description in seed data (`LMS9001`) | `V90__demo_data.sql` | RAG-01, RAG-03 | none | Planned / Unsupported |

## 3. Coverage summary

| Area | Scenario rows | Implemented-passing | Planned deterministic | Blocked by decision/need | Unsupported |
|---|---|---|---|---|---|
| Login (P) | 11 | 8 | 1 (LOGIN-08) | 2 (LOGIN-09, 11) | 0 |
| Session/logout | 3 | 0 | 0 | 0 | 3 |
| Payment | 6 | 0 | 0 | 0 | 6 |
| Catalog courses | 10 | 4 | 6 | 0 | 0 |
| Enrollments + service | 10 | 0 | 8 | 2 (ENR-06, 07) | 0 |
| Security/exposure | 10 | 0 | 6 (SEC-01…07 except SEC-08) | 3 (SEC-08…10) + SEC-02 review | 0 |
| UI/a11y | 10 | 0 | 1 (UI-10, CI) | 9 | 0 |
| Regression | 6 | 2 | 3 | 1 (docs review) | 0 |
| RAG | 5 | 0 | 1 (RAG-01) | 1 (RAG-02) | 3 |

("Implemented-passing" counts scenarios, not test methods. The 24 `LoginServiceTest` methods and 10 catalog tests were all run and passed in Stages 4–5.)

## 4. Gaps in traceability
- No traceable requirement exists for payment, session, logout, or RAG. They appear only in the brief.
- No source states the intended behavior for missing enrollment fields (OBS-E4) or malformed request bodies (OBS-C7); expected results there are deliberately left as "observe first".
- The brief's "login page" and "payment page" selectors/routes cannot be traced to any file.
