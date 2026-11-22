Good — `CourseMapper.java:29` and `:33` confirm `getCredits()` and `getCapacity()` are separate fields on the Course domain object, which makes the fixture-assertion mismatch in `CourseControllerTest.java:55/64` even more concrete: whichever 6-arg constructor arg maps to capacity, the value used (20) and the asserted value (30) cannot both be right unless the Course domain class defaults capacity to exactly 30 when it isn't passed explicitly. `Course.java` was not in the reading list and cannot be checked.

Continuing the report:

---

## Part 4 — Unknowns (continued)

| # | Unknown | Affects rows |
|---|---|---|
| U-01 | `Course.java` constructor: is 5th arg `credits` or `capacity`? This determines whether the CAT-02 assertion `$.capacity.value(30)` is correct or wrong. | CAT-02 (F-01 above) |
| U-02 | `api/catalog.js`: URL built for level filter; exact error message string on non-2xx | UI-03, UI-05 |
| U-03 | `constants.js`: `LEVEL_FILTERS` array values; `formatLevel` output strings | UI-01, UI-05, UI-07 |
| U-04 | `LearnerService.java`: whether `getByRef()` throws `LearnerNotFoundException` | ENR-09 |
| U-05 | `CourseRepository.java`: whether keyword search uses bound parameters or string concatenation. F11 in strategy says bound parameters [SRC], but `CourseRepository.java` was not in the reading list — the strategy's F11 itself depends on a file not provided here. | SEC-09, SEC-10 |
| U-06 | `CourseNotFoundException.java`, `CourseAlreadyArchivedException.java`, etc.: what `getMessage()` returns. Matrix rows that assert a specific `message` field value (CAT-05, CAT-08) rely on the exception constructors not seen here. | CAT-05, CAT-08 |
| U-07 | Logback/SLF4J appender configuration: SEC-05 says newline is written verbatim at the SLF4J substitution layer (`CatalogAuditLogger.java:27`), which is confirmed; whether the final appender re-escapes it is configuration-dependent and no `logback.xml`/`application.yml` was read. | SEC-05 |
| U-08 | CI workflow file not read: REG-05 "current CI is Python-only" and UI-10 "not automated in CI" cannot be confirmed. | REG-05, UI-10 |
| U-09 | `EnrollmentMapper.java` not read: ENR-01's claim about the enrollment response body fields is unverifiable. | ENR-01 |

---

## Part 5 — Unsupported Scenarios in the Matrix That Are Correctly Labelled

The following are **confirmed as correctly unsupported** from source:

- `UNSUP-SES-01..03`: No session, token, cookie, or router in any component. `App.jsx` has no router. `SecurityConfig.java` has no session management. ✓
- `UNSUP-PAY-01..06`: No payment code, route, gateway interface, or UI payment form in any file read. `Main.java:6` even names the project "Payment application starter project" but nothing payment-related exists beyond that string. ✓
- `RAG-03..05`: No RAG/LLM component in any file read. ✓

---

## Part 6 — Missing Scenarios Recommended for Addition

Each has: ID, risk, priority, preconditions, steps, expected observable result, layer, evidence, deterministic or AI-evaluated.

---

**MS-01 — Archive / deactivate / reactivate on a nonexistent course code → 404**

| Field | Value |
|---|---|
| Risk | Untested exception-propagation path; a refactor of `getByCode` could silently swallow the exception |
| Priority | P2 |
| Preconditions | `@WebMvcTest(CourseController.class)` with `courseService` mocked to throw `CourseNotFoundException` |
| Steps | `POST /api/courses/GHOST/archive` with valid body; `POST /api/courses/GHOST/deactivate`; `PUT /api/courses/GHOST` with valid body |
| Expected | 404, body `{"error":"COURSE_NOT_FOUND","message":"…"}` for each |
| Layer | API (`@WebMvcTest`) |
| Evidence | `CourseService.java:49` (`update` calls `getByCode`), `CourseService.java:63`, `CourseService.java:77`, `CourseService.java:84`; `CatalogExceptionHandler.java:16-21` |
| Det/AI | Deterministic |

The matrix has CAT-05 for `GET /api/courses/{code}` → 404, but the write endpoints are not explicitly covered.

---

**MS-02 — Enrollment path: CourseNotFoundException surfaces as 404 from EnrollmentController**

| Field | Value |
|---|---|
| Risk | Two `@RestControllerAdvice` classes coexist; a future ordering change could suppress `CatalogExceptionHandler` for requests that go through `EnrollmentController` |
| Priority | P1 |
| Preconditions | `@WebMvcTest(EnrollmentController.class)` with real security off; `enrollmentService` mocked to throw `CourseNotFoundException` |
| Steps | `POST /api/enrollments {"courseCode":"GHOST","learnerRef":"L-00001"}` |
| Expected | 404, `{"error":"COURSE_NOT_FOUND","message":"…"}` |
| Layer | API |
| Evidence | `EnrollmentService.java:26` (`courseService.getByCode(courseCode)` is the first call); `CatalogExceptionHandler.java:16-21` |
| Det/AI | Deterministic |

ENR-02 and ENR-08 cover the happy path and known enrollment exceptions but not this cross-handler path.

---

**MS-03 — `instructorEmail` null/absent accepted by `CourseUpdateRequest`**

| Field | Value |
|---|---|
| Risk | `@Email` and `@Size` without `@NotNull` on `instructorEmail` (`CourseUpdateRequest.java:34-35`) means a PUT body omitting `instructorEmail` entirely passes validation; this is an intentional design choice but not characterised in CAT-06 |
| Priority | P2 |
| Preconditions | `@WebMvcTest(CourseController.class)`; service mocked to return a course |
| Steps | `PUT /api/courses/CS1007` with valid body but no `instructorEmail` field |
| Expected | 200 (validation passes; `instructorEmail` is optional) |
| Layer | API |
| Evidence | `CourseUpdateRequest.java:34-35` (no `@NotNull`); `CourseUpdateRequest.java:14` comment states the field is nullable |
| Det/AI | Deterministic |

CAT-06 test data lists `"email not-an-email, valid, 121 chars"` but never lists the absent/null case. The boundary is documented in F4 of the strategy as valid email ≤ 120, which implies optional, but the matrix row doesn't explicitly test the null-is-valid boundary.

---

**MS-04 — `courseService.getByCode` is case-insensitive; `GET /api/courses/cs3401` resolves same as `CS3401`**

| Field | Value |
|---|---|
| Risk | Callers may receive a 200 for a mixed-case code and assume the API is case-sensitive; callers who store the code returned in the response may store the original mixed-case form |
| Priority | P3 |
| Preconditions | Needs MySQL (can only be a characterization note in mock-only mode) |
| Steps | `GET /api/courses/cs3401` (lowercase) with repository returning a `CS3401` course |
| Expected | Undetermined in mock-only mode; in a real DB run, 200 with code=`"CS3401"` (the stored value) |
| Layer | API; blocked (needs DB) |
| Evidence | `CourseService.java:43`: `courseRepository.findByCodeIgnoreCase(code)` |
| Det/AI | Deterministic once DB available |

No matrix row mentions this behaviour at all.

---

**MS-05 — `EnrollmentCreateRequest` with missing fields reaches service with `null` arguments**

| Field | Value |
|---|---|
| Risk | `@Valid` with no constraints passes, then `enrollmentService.enroll(null, null)` → `courseService.getByCode(null)` → JPA call with null; could throw `NullPointerException` rather than a mapped 4xx |
| Priority | P1 |
| Preconditions | `@WebMvcTest(EnrollmentController.class)` with services mocked; OR full Spring context with real service |
| Steps | `POST /api/enrollments {}` (empty body); `POST /api/enrollments {"courseCode":null}` |
| Expected | **Undetermined — observe actual status first** (per ENR-07, which is correctly Blocked(Q6)); this row adds the explicit note that null propagation could yield 500, not 400 |
| Layer | API |
| Evidence | `EnrollmentCreateRequest.java:1-12` (no constraints); `EnrollmentController.java:28` (`@Valid`); `EnrollmentService.java:26` |
| Det/AI | Deterministic once observable |

ENR-07 covers this but marks it blocked. This MS-05 adds the specific risk that the failure mode may be a 500 rather than 400 — a meaningful distinction for the blocked-decision description.

---

**MS-06 — `LearnerController` returns 200 with `[]` when learner has zero enrollments**

| Field | Value |
|---|---|
| Risk | UI or API client may treat an empty list as an error |
| Priority | P3 |
| Preconditions | `learnerService.getByRef` mocked to return a learner; `enrollmentRepository.findByLearnerOrderByCreatedAtDesc` mocked to return empty list |
| Steps | `GET /api/learners/L-00001/enrollments` |
| Expected | 200, `[]` |
| Layer | API |
| Evidence | `LearnerController.java:30-35` (returns `List<EnrollmentResponse>` with no explicit 200 annotation; Spring defaults to 200) |
| Det/AI | Deterministic |

ENR-09 covers the 200-with-data and 404 paths but not the 200-with-empty-list path.

---

**MS-07 — `LoginService`: Unicode non-whitespace that `String.isBlank()` does not treat as blank**

| Field | Value |
|---|---|
| Risk | `String.isBlank()` uses `Character.isWhitespace()`, which returns `false` for U+00A0 (NO-BREAK SPACE). A password consisting solely of `"\u00A0"` would not be treated as blank, would pass the guard, and would be compared against the stored password — returning `false` correctly, but the guard's intent is violated |
| Priority | P2 |
| Preconditions | Same as LOGIN-04 |
| Steps | `login(DEMO_USERNAME, "\u00A0")`; `login("\u00A0", demoPassword)` |
| Expected | `false`, no exception (the lookup or compare will reject it, but the blank-guard is bypassed) — this is a **characterization**, not a security hole |
| Layer | Unit |
| Evidence | `LoginService.java:47-49` (delegates to `String.isBlank()`); Java docs for `Character.isWhitespace` exclude U+00A0 |
| Det/AI | Deterministic |

LOGIN-08 mentions "Unicode" as an input category, but the specific U+00A0 edge case (a character that looks blank but isn't) is a distinct boundary not called out there.

---

**MS-08 — Deactivate returns 200 with `active=false` in response body**

| Field | Value |
|---|---|
| Risk | CAT-10 records the missing open-enrolment guard as a known gap, but does not assert what the response body currently looks like |
| Priority | P3 |
| Preconditions | `courseService.deactivate` mocked to return a Course with `active=false` |
| Steps | `POST /api/courses/CS1007/deactivate` |
| Expected | 200; `$.active = false` |
| Layer | API |
| Evidence | `CourseController.java:59-60`; `CourseService.java:76-81` |
| Det/AI | Deterministic |

CAT-03 characterises reactivate (active=true); there is no symmetric row for deactivate.

---

## Part 7 — What Could Not Be Verified

| Item | Reason |
|---|---|
| `Course.java` constructor argument mapping | File not in provided reading list; critical for resolving F-01 |
| `api/catalog.js` fetch error message format | File not in reading list |
| `constants.js` level labels and filter values | File not in reading list |
| `CourseRepository.java` query implementation | File not in reading list; F11 / SEC-09 / SEC-10 rely on it |
| `LearnerService.java` exception type | File not in reading list |
| `EnrollmentMapper.java` response fields | File not in reading list |
| All exception class constructors / `getMessage()` | Not in reading list |
| Logback appender configuration | No `logback.xml` or `application.yml` in reading list |
| CI workflow | Not in reading list; REG-05, UI-10 CI claims unconfirmable |
| Any "RUN" evidence in the matrix | No test was executed by me; all pass/fail claims in the matrix are reproduced as *reported in `docs/test-matrix.md`, not re-run by me* |
| Seed data file `V90__demo_data.sql` | Not in reading list; F14 (embedded AI instructions in `LMS9001` description) referenced by matrix but content not seen by this reviewer |

---

## Consolidated Verdict by Matrix Section

| Section | Status |
|---|---|
| §1 Login (LOGIN-01..10) | Structurally sound. One count error (F-04). LOGIN-11 correctly blocked. |
| §2 Session (UNSUP-SES) | Correctly unsupported (confirmed from source). |
| §3 Payment (UNSUP-PAY) | Correctly unsupported (confirmed from source). |
| §4 Catalog courses (CAT-01..10) | **CAT-02 "Implemented-passing" is suspect** (F-01). CAT-05..10 "Not started" confirmed. CAT-01, CAT-03, CAT-04 structurally sound pending F-01 resolution. CAT-09 expected-result correctly deferred. |
| §5 Enrollments (ENR-01..09, SVC-01) | All "Not started" rows are consistent with source. ENR-07 unknown-result is correctly flagged. ENR-03 `>=` check and null-capacity bypass confirmed. Missing: cross-handler path for `CourseNotFoundException` in enrollment (MS-02). |
| §6 Abuse / security (SEC-01..10) | SEC-02 (`addFilters=false`) confirmed. SEC-03 field exclusion confirmed. SEC-05 log-injection finding confirmed with SLF4J caveat (F-07). SEC-04 `message` field absence in three enrollment 409s creates a potential future test-writing trap (F-06). |
| §7 UI (UI-01..10) | UI-01, UI-04, UI-06, UI-07, UI-08 confirmed from source. UI-03 partially unverifiable (F-05). UI-05 URL format unverifiable (U-02). All correctly blocked on Q5. |
| §8 Regression (REG-01..06) | REG-01 level logic confirmed. REG-02 facultyOf expected values confirmed. REG-03 "do not guess" caveat is now obsolete (F-08). REG-04 field-exclusion gap confirmed. |
| §9 RAG (RAG-01..05) | RAG-03..05 correctly unsupported. RAG-01 and RAG-02 preconditions sound. |
