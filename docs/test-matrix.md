# Test Matrix

Companion to `docs/test-strategy.md`. **Nothing here is implemented by this document.** The "Current status" column records only what exists today (verified by running the suites in Stages 4–5).

Legend
- Components: **P** = `payment-login-java-project`; **C-API** = `starter_code/backend`; **C-UI** = `starter_code/frontend`.
- Priority: P0 highest … P3 lowest (see strategy §3).
- Evidence: **SRC** source read, **RUN** observed by executing, **DOC** documentation only, **NONE** no evidence (unsupported).
- Det/AI: **D** = deterministic (fixed inputs, no network/time/randomness); **AI** = needs model/rubric evaluation.
- Status: `Implemented-passing` (exists, run, output inspected) · `Not started` · `Blocked(Qn)` (needs decision) · `Unsupported`.
- All test data is synthetic. The only login fixture is the account already defined in `UserRepository` (`demo.user`); its password is read from the fixture in tests, never copied into docs or code.

Counts: **71 scenario rows**: 12 unsupported placeholders (UNSUP-SES-01..03, UNSUP-PAY-01..06, RAG-03..05) and 59 supported or blocked-by-decision rows. §10 lists 17 groups of deterministic candidates not yet automated (target ≥ 10, exceeded).

---

## 1. Login (P) — requirement sources: capstone brief + `LoginService` source

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| LOGIN-01 | Valid credentials authenticate | Valid users locked out / auth broken | P0 | `UserRepository` default fixture | `demo.user` + fixture password | `login(u,p)` | `true` | Unit | SRC `LoginService`, RUN | D | Implemented-passing (`loginWithValidCredentialsShouldSucceed`) |
| LOGIN-02 | Wrong password rejected | Auth bypass | P0 | same | `demo.user` + `not-the-password` | `login` | `false`, no exception | Unit | SRC, RUN | D | Implemented-passing |
| LOGIN-03 | Unknown user rejected | Auth bypass | P0 | same | `no.such.user` + valid-length password | `login` | `false` | Unit | SRC, RUN | D | Implemented-passing |
| LOGIN-04 | Blank/null username or password rejected without exception | Crash, bypass via empty compare | P0 | same | `null`, `""`, `" "`, `"\t"`, `"\n"` in each field; both null | `login` | `false`; no exception | Unit (parameterized) | SRC, RUN | D | Implemented-passing (5 values × 2 fields + both-null) |
| LOGIN-05 | Malformed / near-miss usernames are not matched (whitespace, case, injection-style text) | Normalization bug lets wrong user in | P1 | same | `" demo.user"`, `"demo.user "`, `"DEMO.USER"`, `"' OR '1'='1"` | `login` | `false` for all | Unit | SRC, RUN | D | Implemented-passing. Email-format rules **not** asserted (A2) |
| LOGIN-06 | Password is case-sensitive and exact (no prefix/suffix match) | Weak compare | P1 | same | upper/lower-cased, +1 char, −1 char | `login` | `false` | Unit | SRC, RUN | D | Implemented-passing |
| LOGIN-07 | Unknown user and wrong password are indistinguishable | User enumeration | P1 | same | as above | compare both results | both `false`; return type is boolean only | Unit | SRC, RUN | D | Implemented-passing (limited: API cannot expose a message) |
| LOGIN-08 | Boundary inputs: very long (e.g., 100,000 chars), Unicode, embedded NUL | Exception, hang, or mismatch on extreme input | P2 | same | 100k-char strings; `"ünïcode"`; `"a\u0000b"` | `login` | `false`, returns promptly, no exception | Unit | SRC (derived) | D | Not started |
| LOGIN-09 | Repeated invalid attempts | Brute force possible: no lockout/rate limit exists | P1 | same | 1,000 wrong attempts then correct | loop `login` | **Current behavior:** correct password still succeeds (no lockout). Characterization only; real expectation depends on Q2 | Unit | SRC (absence) | D | Blocked(Q2) |
| LOGIN-10 | Null repository rejected at construction | Misconfiguration surfaces late | P3 | none | `null` | `new LoginService(null)` | `IllegalArgumentException` | Unit | SRC, RUN | D | Implemented-passing |
| LOGIN-11 | Credential storage is plaintext | Credential exposure in memory/heap/logs | P1 | none | n/a | read `UserRepository` / `User` | Documented defect (F2); no test can pass "correctly" until hashing exists | Review | SRC | D (static) | Blocked(Q2) |

## 2. Session, logout, protected pages — UNSUPPORTED

| ID | Requested scenario | Why unsupported | Status |
|---|---|---|---|
| UNSUP-SES-01 | Logout invalidates session | No session/token/logout in P, C-API or C-UI (strategy §2.4) | Unsupported (Q3) |
| UNSUP-SES-02 | Protected page after logout redirects/denies | No protected page or router exists; `/login` and `/payment` return the SPA shell only [RUN] | Unsupported (Q3) |
| UNSUP-SES-03 | Session fixation / expiry / cookie flags | No cookies/sessions | Unsupported |

## 3. Payment — UNSUPPORTED (conditional design only)

No payment code, route, SDK, UI, or gateway reference exists (inspection §10, §17). These are **placeholders**, not candidates.

| ID | Requested scenario | Would require | Status |
|---|---|---|---|
| UNSUP-PAY-01 | Payment field validation | A payment module/form | Unsupported (Q1) |
| UNSUP-PAY-02 | Mock payment success | In-process fake gateway interface (no live gateway, per CLAUDE.md #3) | Unsupported (Q1) |
| UNSUP-PAY-03 | Mock payment decline | same | Unsupported (Q1) |
| UNSUP-PAY-04 | Mock payment timeout | Fake gateway with injected clock/timeout | Unsupported (Q1) |
| UNSUP-PAY-05 | Idempotency / double submit of a payment | Idempotency key design | Unsupported (Q1) |
| UNSUP-PAY-06 | Card/PII not exposed in logs or responses | Payment data handling | Unsupported (Q1) |

The closest **real** analogue of idempotency/repeated submission is enrollment (§5, ENR-05/ENR-06).

## 4. Catalog API: course endpoints (C-API) — verified in controllers/DTOs

All run as `@WebMvcTest` with mocked services (no DB). Error-body field names come from `CatalogExceptionHandler`.

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| CAT-01 | List returns active courses | Wrong data to UI | P2 | `CourseService` mocked | 2 synthetic courses | `GET /api/courses` | 200; array of 2; summary fields | API | SRC, RUN | D | Implemented-passing (`listsActiveCourses`) |
| CAT-02 | Detail by code | Wrong course/fields | P2 | mocked | `CS3401` | `GET /api/courses/CS3401` | 200; code, level, capacity | API | SRC, RUN | D | Implemented-passing (`returnsCourseDetailByCode`) |
| CAT-03 | Reactivate success | State bug | P2 | mocked | `CS9001` | `POST …/reactivate` | 200; `active=true` | API | SRC, RUN | D | Implemented-passing |
| CAT-04 | Reactivate when already active → conflict | Wrong status code | P2 | mocked to throw | `CS9999` | `POST …/reactivate` | 409 `COURSE_ALREADY_ACTIVE` | API | SRC, RUN | D | Implemented-passing |
| CAT-05 | Unknown course → 404 | Leaks stack trace/wrong code | P2 | mocked to throw `CourseNotFoundException` | `NOPE000` | `GET /api/courses/NOPE000` | 404 `COURSE_NOT_FOUND` + message | API | SRC | D | Not started |
| CAT-06 | Update validation boundaries | Invalid data persisted | P1 | valid-body helper | credits 4/5/60/61; capacity 0/1/500/501; title blank/200/201 chars; description 2000/2001; email `not-an-email`, valid, 121 chars | `PUT /api/courses/CS1007` | each invalid → 400 `VALIDATION_FAILED` with the offending field in `fields`; each boundary-valid → service called (mock returns course) → 200 | API (parameterized) | SRC (DTO annotations) | D | Not started |
| CAT-07 | Archive reason rules | Unaudited archive; oversize input | P1 | mocked | reason `""`, `"  "`, 500 chars, 501 chars | `POST …/archive` | blank/501 → 400 `VALIDATION_FAILED`; 500 → 200 | API | SRC | D | Not started |
| CAT-08 | Archive twice / update archived → conflict | Data corruption | P1 | mocked to throw | `CS2110` | archive, `PUT` | 409 `COURSE_ALREADY_ARCHIVED`; 409 `COURSE_ARCHIVED` | API | SRC | D | Not started |
| CAT-09 | Malformed request body | 500 instead of 4xx | P2 | none | invalid JSON; wrong type `"credits":"abc"`; unknown `level` query value | `PUT`/`GET ?level=BOGUS` | 4xx (exact code **unverified**: confirm by running before asserting) | API | NONE (behavior not yet observed) | D | Not started; exact status to be observed first |
| CAT-10 | `deactivate` has no guard for open enrolments | Business-rule gap noted in source TODO | P2 | mocked | `CS1007` | `POST …/deactivate` | Currently 200; records TODO as known gap | API | SRC (TODO comment) | D | Not started; characterization only |

## 5. Catalog API: enrollments and learners (C-API)

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ENR-01 | Successful enrollment | Seats not granted | P1 | mocked service | `CS1007`, `L-00001` | `POST /api/enrollments` | 201 with enrollment body | API | SRC | D | Not started |
| ENR-02 | Error mapping | Wrong status for known failures | P1 | mocked service throws each exception | duplicate, seat-unavailable, course-not-open, learner-not-found | `POST` | 409 `DUPLICATE_ENROLLMENT`; 409 `SEAT_UNAVAILABLE`; 409 `COURSE_NOT_OPEN`; 404 `LEARNER_NOT_FOUND` | API (parameterized) | SRC (`EnrollmentExceptionHandler`) | D | Not started |
| ENR-03 | Capacity boundary in service | Overbooking | P0 | Mockito repos; capacity = 2 | active seats 1 → ok; 2 → reject | `EnrollmentService.enroll` | seats 1 → saved; seats = capacity → `SeatUnavailableException`; null capacity → allowed (per code) | Unit | SRC | D | Not started |
| ENR-04 | Inactive course refused | Enrolment in closed course | P0 | course `active=false` | `CS9001` | `enroll` | `CourseNotOpenException`; no save | Unit | SRC | D | Not started |
| ENR-05 | Duplicate ACTIVE enrolment refused (repeated submission) | Double booking | P0 | repo returns existing ACTIVE | same course+learner twice | `enroll` | `DuplicateEnrollmentException`; `save` never called | Unit | SRC | D | Not started |
| ENR-06 | Idempotency under concurrency | Race → duplicates; no DB unique constraint (F8) | P0 | needs real DB + threads | n/a | n/a | Cannot be proven with mocks | n/a | SRC (V5 migration) | n/a | Blocked: mock-only decision; recorded as unverified risk |
| ENR-07 | Missing/blank `courseCode` or `learnerRef` | `@Valid` does nothing (F7); behavior unknown | P1 | mocked service | `{}`, nulls, blanks | `POST` | **Unknown.** First observe actual status; do not assume 400 | API | SRC (absence of constraints) | D | Blocked(Q6) |
| ENR-08 | Check order: course lookup → active → learner → duplicate → capacity | Wrong error precedence | P2 | Mockito | unknown course + unknown learner | `enroll` | `CourseNotFoundException` first | Unit | SRC | D | Not started |
| ENR-09 | Learner enrollments list | Wrong/foreign data | P2 | mocked | `L-00001` | `GET /api/learners/L-00001/enrollments` | 200 list; unknown learner → 404 `LEARNER_NOT_FOUND` | API | SRC | D | Not started |
| SVC-01 | `listActive` filters and trims keyword | Wrong query path | P2 | Mockito repo + audit logger | blank / `" cs "` / level only / both | `listActive` | blank keyword → level/no-level repository method; keyword trimmed; audit `recordCatalogRead("anonymous", n)` called | Unit | SRC | D | Not started |

## 6. Authentication/authorization abuse and sensitive information (C-API, P)

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| SEC-01 | **Characterization:** anonymous writes succeed (F3) | Anyone can edit/archive/deactivate/enroll | P0 | `@WebMvcTest` with `SecurityConfig` imported and filters **on**; services mocked | no credentials, no CSRF token | `PUT`, `POST archive`, `POST deactivate`, `POST /api/enrollments` | **Current:** 2xx for all. Test is labeled a known-defect characterization; flips to expect 401/403 once Q4 is decided. Must not be silently weakened | API | SRC (`SecurityConfig`) | D | Not started; Blocked(Q4) for final expectation |
| SEC-02 | Existing controller tests never exercise security | Regression in `SecurityConfig` goes unnoticed | P1 | n/a | `addFilters=false` in `CourseControllerTest` | review | Gap recorded; closed by SEC-01 | Review | SRC | D | Open |
| SEC-03 | List vs. detail field exposure (F9) | Public PII/internal data | P1 | mocked | course with email + archive reason | `GET` list; `GET` detail | list JSON has **no** `description`, `instructorEmail`, `archiveReason`; detail JSON currently has them (characterization) | API | SRC | D | Not started |
| SEC-04 | Error bodies expose only code/message | Stack-trace/internal leak | P1 | mocked exceptions | 404/409/400 cases | read bodies | bodies contain only `error`, `message` or `fields`; no class names/stack | API | SRC | D | Not started |
| SEC-05 | Log injection via archive `reason` (F10) | Forged log lines / audit tampering | P1 | `OutputCaptureExtension`; real `CatalogAuditLogger` | reason `"x\nactor=admin action=COURSE_ARCHIVED"` | `recordCourseArchived` | **Current:** newline written verbatim (characterization of defect) | Unit | SRC | D | Not started |
| SEC-06 | Login never logs/echoes credentials | Secret leakage | P1 | capture stdout/stderr | fixture creds | login success + failure | captured output does not contain username/password | Unit | SRC (no logging exists) | D | Not started |
| SEC-07 | Secret scan: payment project | Committed credential regression | P1 | file read of repo tree | n/a | scan `src/` and `pom.xml` for secret patterns, allowing the documented synthetic fixture only in `UserRepository` | no matches outside the allow-listed fixture | Static | SRC | D | Not started |
| SEC-08 | Secret scan: catalog | Known committed credentials (`application.yml`, `db-setup`, `.vscode/mcp.json`) | P1 | same | n/a | same scan on `starter_code` | **Currently fails** (known issue, Q7); keep as tracked known-failing, not deleted or weakened | Static | SRC | D | Blocked(Q7) |
| SEC-09 | SQL-injection-style keyword | Injection | P1 | needs DB | `' OR 1=1 --` | `GET /api/courses?q=…` | Source shows bound parameters (F11); effect on a real DB cannot be observed without MySQL | API | SRC | D | Blocked: needs DB |
| SEC-10 | LIKE wildcards in keyword not escaped | Unexpected matches | P3 | needs DB | `%`, `_` | search | Unknown without DB | API | SRC (F11) | D | Blocked: needs DB |

## 7. UI and accessibility (C-UI) — all blocked on Q5 (no UI test tool installed)

Observed facts: [SRC+RUN] page title "Global Learning — Course catalogue", heading "Course catalogue", level-filter group, loading/error/empty texts.

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| UI-01 | Initial load shows heading and filter group | Broken page | P2 | `fetch` stubbed to return 2 courses | synthetic courses | open `/` | heading "Course catalogue"; group "Filter by level"; 2 list items showing code, level label, title, credits | UI | SRC, RUN (shell) | D | Blocked(Q5) |
| UI-02 | Loading state | Blank screen | P3 | stub with pending promise | none | open `/` | text "Loading the catalogue…" visible | UI | SRC | D | Blocked(Q5) |
| UI-03 | API error state | Silent failure | P2 | stub returns 500 | status 500 | open `/` | message "Catalogue request failed with status 500" + port hint | UI | SRC, RUN (proxy 500) | D | Blocked(Q5) |
| UI-04 | Empty state | Confusing empty page | P3 | stub returns `[]` | none | open `/` | "No courses match this filter. Clear the filter to see the full catalogue." | UI | SRC | D | Blocked(Q5) |
| UI-05 | Level filter triggers request | Wrong filter | P2 | stub records URLs | click "Intermediate" then "All levels" | click buttons | requests `/api/courses?level=INTERMEDIATE`, then `/api/courses` | UI | SRC | D | Blocked(Q5) |
| UI-06 | Stale response ignored (cancel flag) | Race shows wrong list | P2 | stub with delayed first response | two quick filter clicks | click | UI shows the result of the last click only | UI | SRC (`cancelled` flag) | D | Blocked(Q5) |
| UI-07 | Keyboard smoke: filter buttons reachable and activatable | Keyboard-only users blocked | P2 | page loaded | Tab, Enter, Space | keyboard | buttons receive focus in order; Enter/Space triggers filter | UI | SRC (native `<button>`) | D | Blocked(Q5) |
| UI-08 | Selected filter exposed to assistive tech | A11y gap: only CSS class marks selection (F12) | P3 | page loaded | click "Advanced" | inspect accessible state | **Currently** no `aria-pressed`/`aria-current`; characterize and record as a11y defect | UI | SRC | D | Blocked(Q5) |
| UI-09 | Credits text for 1 credit | `"1 credits"`; `formatCredits` helper unused | P3 | stub course credits=1 | `credits:1` | render | **Currently** "1 credits" (API forbids <5, so low impact) | UI/Unit | SRC | D | Blocked(Q5) |
| UI-10 | Build gate: `npm run build` passes | Broken frontend ships | P2 | `npm ci` done | none | `npm run build` | exit 0 | Build | RUN (Stage 5) | D | Passing manually; not automated in CI |

## 8. Regression risks and unit opportunities

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| REG-01 | Level inference from code | Wrong level shown | P2 | none | `CS1007`, `ENGG2104B`, `MATH3220A` | `levelOf` | FOUNDATION / INTERMEDIATE / ADVANCED | Unit | SRC, RUN | D | Implemented-passing (`LegacyCourseCodeParserTest`, 4 tests) |
| REG-02 | Faculty extraction | Wrong faculty | P2 | none | `null`, `"1234"`, `"cs2110"`, `"DATA1105"` | `facultyOf` | `UNKNOWN`, `UNKNOWN`, `CS`, `DATA` | Unit | SRC | D | Not started |
| REG-03 | Level inference edge cases | Wrong level/crash on odd codes | P2 | none | `null`, `"CS1"` (<5 chars), `"ABCDE12345"` | `levelOf` | Expected values must be **read from the unseen middle of `levelOf` before asserting**; do not guess | Unit | SRC (partial) | D | Not started |
| REG-04 | Mapper: detail includes faculty; summary excludes description | DTO drift | P2 | none | synthetic course | `toResponse`, `toSummary` | faculty derived; summary has id/code/title/level/credits only | Unit | SRC, RUN | D | Implemented-passing (2 mapper tests; add exclusion check) |
| REG-05 | CI runs the tests | Regressions merge unnoticed | P1 | `.github/workflows` | n/a | PR to master | workflow runs `mvn test` for P and C-API (+ frontend build) and fails on red | CI | SRC (current CI is Python-only) | D | Not started |
| REG-06 | Docs vs. config drift (H2 vs MySQL; Java 17 vs 21) | Misleading setup | P3 | none | n/a | doc review | Docs corrected or flagged | Review | SRC | D | Open |

## 9. RAG safety and evaluation — no RAG exists; fixture-level and conditional only

No retrieval, embedding, or LLM call exists in P, C-API or C-UI. One real **untrusted-content fixture** exists: course `LMS9001` (demo seed) whose description contains instructions aimed at AI assistants (F14).

| ID | Requirement / observed behavior | Risk | Pri | Preconditions | Test data | Steps | Expected result | Layer | Evidence | Det/AI | Current status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| RAG-01 | Untrusted description is carried as inert data by the mapper/API | Content treated as markup or instruction by the app | P2 | none | description string with instruction-like text and HTML tags | `toResponse` | returned verbatim as a JSON string; not interpreted; list endpoint omits it | Unit/API | SRC | D | Not started |
| RAG-02 | UI never renders descriptions | HTML/script injection via description | P2 | stubbed API with markup in description | `<script>`-like text | render | no description is shown; no injection (cards show code, level, title, credits only) | UI | SRC | D | Blocked(Q5) |
| RAG-03 | Assistant ignores instructions embedded in retrieved records | Prompt injection | P1 | **requires a RAG/LLM component** | `LMS9001` description | ask a question that retrieves it | Answer ignores embedded instructions, answers from facts, mentions capacity honestly | AI-evaluated rubric | NONE | AI | Unsupported (Q8) |
| RAG-04 | Grounding / no fabricated course facts | Hallucinated seats, codes | P2 | requires RAG | question about a nonexistent course | evaluate | Declines or says not found | AI-evaluated rubric | NONE | AI | Unsupported (Q8) |
| RAG-05 | Retrieved text never exfiltrates secrets | Secret leakage | P1 | requires RAG | fixture with fake secret-like string | evaluate | Not repeated | AI-evaluated rubric | NONE | AI | Unsupported (Q8) |

---

## 10. Deterministic automation candidates (summary)

Supported, deterministic, runnable without DB or new dependency (target for ≥ 8 stable new tests):

| # | ID | Layer | Pri |
|---|---|---|---|
| 1 | CAT-05 | API | P2 |
| 2 | CAT-06 | API (parameterized) | P1 |
| 3 | CAT-07 | API | P1 |
| 4 | CAT-08 | API | P1 |
| 5 | ENR-01, ENR-02 | API | P1 |
| 6 | ENR-03 | Unit | P0 |
| 7 | ENR-04 | Unit | P0 |
| 8 | ENR-05 | Unit | P0 |
| 9 | SEC-01 | API (real filter chain) | P0 |
| 10 | SEC-03 | API | P1 |
| 11 | SEC-04 | API | P1 |
| 12 | SEC-05 | Unit (log capture) | P1 |
| 13 | SEC-06 | Unit | P1 |
| 14 | SEC-07 | Static | P1 |
| 15 | LOGIN-08 | Unit | P2 |
| 16 | REG-02, REG-04 (exclusion), RAG-01 | Unit | P2 |
| 17 | REG-05 | CI | P1 |

Already implemented and passing: LOGIN-01…07, LOGIN-10, CAT-01…04, REG-01, REG-04 (partial).

Blocked/unsupported: LOGIN-09, LOGIN-11, ENR-06, ENR-07, SEC-08, SEC-09, SEC-10, UI-* and RAG-02 (Q5/Q2/Q4/Q6/Q7), all UNSUP-*, RAG-03…05.
