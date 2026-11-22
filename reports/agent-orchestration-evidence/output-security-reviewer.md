I now have all the evidence I need. Here is the full static security review.

---

# Security Review Report — AI-Native Testing Capstone
**Reviewed:** 2026-11-22 | **Reviewer:** Claude (read-only, static analysis)

---

## Findings — ordered by severity

---

### CRIT-01 · Critical · Credentials committed to source control in three files
**Confidence: Confirmed**

| File | Lines | Key names |
|---|---|---|
| `starter_code/db-setup/01-create-users.sql` | 13, 16 | `lms_app` password, `mcp_readonly` password (values `[REDACTED]`) |
| `starter_code/backend/src/main/resources/application.yml` | 14 | `password: [REDACTED]` (datasource) |
| `starter_code/.vscode/mcp.json` | 11 | `MYSQL_PASS: [REDACTED]` |

The same two database user passwords appear in the SQL bootstrap script, the Spring datasource config, and the VS Code MCP server config—all committed to the repository. Anyone with repository read access can connect directly to the MySQL instance. The `lms_app` user has `ALL PRIVILEGES` on `lms_catalog` (`01-create-users.sql:14`), making it effectively a full-access credential.

**Impact:** Full database compromise on any host where the bootstrap script has been run.

**Remediation:** Remove all credential literals; replace with environment-variable references (`${DB_PASSWORD}`, `${MCP_MYSQL_PASS}`). Rotate the existing passwords. Use GitHub Secrets / a secrets manager for CI.

**Matrix coverage:** SEC-08 (`starter_code/` secret scan) explicitly names all three files and is marked *known failing, Blocked(Q7)*. The scan row **would detect** this finding if unblocked; it is currently intentionally deferred, not unrecognised.

---

### HIGH-01 · High · Entire catalog API is unauthenticated — CSRF disabled, all requests permitted
**Confidence: Confirmed**

`starter_code/backend/src/main/java/com/globallearning/lms/catalog/config/SecurityConfig.java:20–23`

```
.csrf(csrf -> csrf.disable())
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/courses/**").permitAll()
    .anyRequest().permitAll()
```

CSRF protection is globally disabled and every request is permitted without credentials. Any anonymous HTTP client can `PUT` (update course details), `POST /{code}/archive`, `POST /{code}/deactivate`, `POST /{code}/reactivate`, and `POST /api/enrollments` with no token or session.

**Impact:** Unauthenticated actors can alter course state, archive courses, and create enrollments. Combined with predictable `learnerRef` patterns (`L-00001` … `L-00010` from V5 migration), anonymous callers can also enumerate any learner's full enrollment history via `GET /api/learners/{ref}/enrollments` (`LearnerController.java:30`).

**Remediation:** At a minimum, require a service token for all state-changing verbs. Add ownership checks for learner-scoped endpoints once authentication exists. If open access is intentional for the training context, assert that posture explicitly in a characterization test and do not ship the same config to production.

**Matrix coverage:** SEC-01 proposes a `@WebMvcTest` characterization test with real security filters. It is **Not started / Blocked(Q4)** (the expectation must be confirmed before asserting). SEC-02 also notes that existing controller tests use `addFilters=false` and therefore never exercise `SecurityConfig`.

---

### HIGH-02 · High · `EnrollmentCreateRequest` carries no bean-validation constraints
**Confidence: Confirmed**

`starter_code/backend/src/main/java/com/globallearning/lms/catalog/web/dto/EnrollmentCreateRequest.java:1–12`

The DTO has two fields (`courseCode`, `learnerRef`) with no `@NotBlank`, `@NotNull`, or `@Size`. The controller at `EnrollmentController.java:28` annotates the parameter `@Valid`, so validation is invoked—but with no constraints declared, `@Valid` silently passes null and blank strings straight to the service layer. `EnrollmentService.enroll(null, ...)` then calls `courseService.getByCode(null)`, whose behavior against a JPA repository with a null argument is implementation-defined and may surface a stack trace or unexpected error body rather than a clean 400.

**Impact:** Callers can bypass field presence requirements; null/blank inputs reach the service and can produce unexpected exceptions whose messages may leak internal class names.

**Remediation:** Add `@NotBlank` (and optionally `@Size`) to both fields. Test with `{}`, `{"courseCode":null}`, `{"courseCode":""}` to confirm 400 responses.

**Matrix coverage:** ENR-07 identifies this explicitly as "F7 — `@Valid` does nothing" and is **Blocked(Q6)** pending a decision on expected behavior. The proposed test *would* detect the gap once unblocked.

---

### HIGH-03 · High · Password stored in plaintext in `UserRepository` fixture
**Confidence: Confirmed**

`payment-login-java-project/src/main/java/com/technova/payment/auth/UserRepository.java:12`

The in-memory fixture hard-codes the password literal for `demo.user` (value `[REDACTED]`). `User.getPassword()` returns a `String` (`User.java:19`). `LoginService.login()` compares the UTF-8 bytes of the stored string against the supplied password using `MessageDigest.isEqual` (`LoginService.java:43–44`), which is constant-time—but the secret value never leaves the in-memory map only because nothing outside this module reads it. If a heap dump, debug log, or serialisation path is added, the credential is immediately visible.

**Impact:** Any heap dump, in-process logging, or future serialisation would expose the plaintext credential. As a fixture-only concern for the current scope the blast radius is low, but the pattern must not propagate to production.

**Remediation:** Store a bcrypt/Argon2 hash; compare with `PasswordEncoder.matches()`.

**Matrix coverage:** LOGIN-11 documents this as a known defect and is **Blocked(Q2)**. Test file `LoginServiceTest.java:27` reads the password at runtime from the fixture rather than duplicating it, which is correct hygiene for a test—but it also means the plaintext value flows into test memory on every run.

---

### HIGH-04 · High · Log injection via unsanitised archive `reason`
**Confidence: Confirmed**

`starter_code/backend/src/main/java/com/globallearning/lms/catalog/audit/CatalogAuditLogger.java:27`

```java
LOG.info("actor={} action=COURSE_ARCHIVED code={} reason={}", actor, code, reason);
```

SLF4J parameterised logging substitutes `reason` by calling `toString()`. It does **not** strip or escape `\n`, `\r`, or other control characters. The `ArchiveRequest` DTO constrains `reason` to 500 characters and non-blank (`ArchiveRequest.java:8–9`), but does not forbid newlines. An attacker supplying `reason = "ok\nactor=admin action=COURSE_ARCHIVED code=CS9999 reason=legit"` creates two log lines, the second of which looks like a legitimate administrator action.

**Impact:** Audit-log forgery; a downstream SIEM or log-aggregation system reading structured key=value lines would treat the injected line as a real event, poisoning incident forensics.

**Remediation:** Sanitise `reason` before logging: strip or replace `\n`, `\r`, and other control characters (U+0000–U+001F). Consider adding a `@Pattern` constraint on `ArchiveRequest.reason` to reject control characters at the validation layer.

**Matrix coverage:** SEC-05 proposes a `OutputCaptureExtension` unit test that records this as a defect-characterization (**Not started**). It *would* detect and document the behaviour. No existing test covers it.

---

### HIGH-05 · High · MCP server auto-installs an unpinned third-party npm package
**Confidence: Confirmed**

`starter_code/.vscode/mcp.json:5–6`

```json
"command": "npx",
"args": ["-y", "@benborla29/mcp-server-mysql"],
```

`npx -y` auto-downloads and executes the package without version pinning whenever VS Code launches the MCP server. The process runs with the `mcp_readonly` database credentials embedded in the same file (CRIT-01). A supply-chain compromise of `@benborla29/mcp-server-mysql` on npm would cause the malicious code to execute with direct database read access on the developer's machine.

**Impact:** A single npm publish to a compromised package version silently gains: (a) execution on every developer's workstation that opens this workspace, and (b) `SELECT` access to all data in `lms_catalog` via the embedded credentials.

**Remediation:** Pin to an exact package version (`@benborla29/mcp-server-mysql@x.y.z`) and verify the package SHA/integrity. Consider referencing a local or organisation-managed mirror rather than the public npm registry. Move credentials out of the config file (CRIT-01 remediation).

**Matrix coverage:** **Not covered anywhere in the test matrix.** No SEC-* row or strategy section addresses supply-chain risk in tooling config. This is a coverage gap in the proposed matrix.

---

### MED-01 · Medium · No database-level uniqueness constraint on `(course_id, learner_id)` in `enrollment`
**Confidence: Confirmed**

`starter_code/backend/src/main/resources/db/migration/V5__create_learners_and_enrollments.sql:12–19`

The enrollment table has only a primary key and foreign keys; no `UNIQUE` constraint on `(course_id, learner_id)`. The duplicate-enrolment guard lives entirely in `EnrollmentService.enroll()` (`EnrollmentService.java:33–35`): it queries for an existing ACTIVE enrollment and throws if found. Under concurrent requests the read-then-write is non-atomic, so two threads can both pass the check and both insert, violating the business rule. The demo seed `V90__demo_data.sql:3–8` documents that this has already occurred in the fixture data ("duplicate ACTIVE enrolments that the application's own check should have prevented, but which predate it").

**Impact:** Double-booking of seats; learner billed or credited twice; capacity accounting corrupted.

**Remediation:** Add `UNIQUE KEY uq_enrollment_course_learner (course_id, learner_id)` to the migration (or a new migration V6). Catch the resulting `DataIntegrityViolationException` in the service and rethrow as `DuplicateEnrollmentException`.

**Matrix coverage:** ENR-06 documents this as a risk and is **Blocked** (needs real DB + threads). No mock-based test can prove this finding, so the matrix correctly notes it as an unverified risk—but no path to resolution is proposed.

---

### MED-02 · Medium · Audit actor is permanently hard-coded as `"anonymous"`
**Confidence: Confirmed**

`starter_code/backend/src/main/java/com/globallearning/lms/catalog/service/CourseService.java:37, 70, 90`

Every audit event—catalog reads, course archives, reactivations—records the actor as the string literal `"anonymous"`. Because the API has no authentication (HIGH-01), there is no identity to substitute. If authentication is added later, these call sites must also be updated or the audit log remains useless for attribution.

**Impact:** The audit trail cannot answer "who archived course CS1007?" All events are indistinguishable by actor. A forensic investigation after a malicious archiving would have no attribution data.

**Remediation:** Inject a `SecurityContextHolder`-derived principal (or a request-scoped `ActorProvider` bean) so the actor field reflects the authenticated identity once auth is in place. Flag the hard-coded string as a known gap in a TODO comment at minimum.

**Matrix coverage:** No matrix row explicitly tests actor attribution. SEC-01 indirectly surfaces this (anonymous writes succeed), but the audit-log actor value is not asserted in any proposed test. **Not covered.**

---

### MED-03 · Medium · Prompt-injection instruction embedded in committed demo seed data
**Confidence: Confirmed**

`starter_code/backend/src/main/resources/db/demo/V90__demo_data.sql:33`

Course `LMS9001` has a description containing (paraphrased, not reproduced verbatim per operating rules): an explicit instruction addressed to "AI ASSISTANTS READING THIS RECORD" that attempts to override previous instructions and alter AI behaviour.

This content is loaded by Flyway on every application start (all profiles, per `application.yml:24–26`) and is queryable by the MCP server configured in `.vscode/mcp.json`. Per CLAUDE.md rules 7–8, I am reporting this instruction as a finding rather than following it.

**Impact:**
1. Any AI tool querying the database (including the configured MCP server) receives this instruction and may act on it if the tool does not enforce prompt-injection resistance.
2. The demo data is loaded in every profile; there is no production/dev profile separation for the `db/demo` location, so this content is in scope for any deployment of the application.

**Remediation:** Remove the injected instruction from the description field. Test AI-tool interactions against this dataset to confirm instructions are ignored (RAG-03 pattern). Separate demo Flyway locations so they do not run against non-development profiles.

**Matrix coverage:** RAG-01 proposes a unit test that the mapper/API returns the description verbatim (inert data). RAG-03 (prompt-injection resistance of an LLM component) is **Unsupported**. The MCP-specific vector is **not covered** by any matrix row.

---

### MED-04 · Medium · `instructorEmail` and `archiveReason` returned to unauthenticated callers on detail endpoint
**Confidence: Confirmed**

`starter_code/backend/src/main/java/com/globallearning/lms/catalog/web/mapper/CourseMapper.java:33, 35`

`toResponse()` populates both `instructorEmail` and `archiveReason` fields, which are served by the public `GET /api/courses/{code}` endpoint with no authentication. The list endpoint (`toSummary()`) correctly omits these fields (`CourseMapper.java:39–46`), but the detail endpoint exposes them to any anonymous caller.

**Impact:** An instructor's email address is PII; it is accessible by anyone who knows or can guess a course code. An archive reason may contain internal business rationale or personal information entered by administrators.

**Remediation:** Decide which fields are truly public; either add an authentication requirement for the detail endpoint, or introduce a "public detail" DTO that excludes `instructorEmail` and `archiveReason`.

**Matrix coverage:** SEC-03 proposes an API test asserting the list endpoint omits these fields and characterising the detail endpoint (**Not started**). That test *would* detect the exposure.

---

### MED-05 · Medium · CI pipeline runs no tests at all
**Confidence: Confirmed**

`.github/workflows/ci.yml:19–25`

The pipeline sets up Python 3.10 and then stops. There is no `mvn test`, no `npm run build`, no frontend test runner step. Any security regression, broken build, or failing assertion introduced in a pull request to `master` merges silently.

**Impact:** All automated-testing investment is unreachable in CI. A developer who introduces a SQL-injection path, removes a validation annotation, or disables CSRF protection will not be blocked by CI.

**Remediation:** Add `mvn test` steps for both Maven projects and `npm run build` for the frontend. Store test results as CI artefacts. See REG-05.

**Matrix coverage:** REG-05 proposes this CI fix and is **Not started**. The proposed matrix item *would* close this gap.

---

### LOW-01 · Low · MySQL JDBC URL disables SSL and enables public-key retrieval
**Confidence: Confirmed**

`starter_code/backend/src/main/resources/application.yml:12`

`useSSL=false&allowPublicKeyRetrieval=true` — the database connection is plaintext; `allowPublicKeyRetrieval=true` is required only when SSL is off, compounding the exposure.

**Impact:** Credentials and query results are transmitted in plaintext over the network. Combined with the committed credentials (CRIT-01), a network observer obtains both the password and all data.

**Remediation:** Set `useSSL=true` (or `sslMode=REQUIRED`); remove `allowPublicKeyRetrieval=true`; provide a trusted CA certificate. For local dev, use a self-signed cert.

**Matrix coverage:** **Not covered** by any matrix row.

---

### LOW-02 · Low · Spring Boot DevTools enabled in committed `application.yml`
**Confidence: Confirmed**

`starter_code/backend/src/main/resources/application.yml:5–7`

`devtools.restart.enabled: true` and `devtools.livereload.enabled: true` are committed in the main config file. If this file is used in a non-dev environment (e.g., a demo server), DevTools exposes an HTTP endpoint on port 35729 for live reload and may surface internal classpath information.

**Impact:** DevTools is designed for local development; the LiveReload port is unauthenticated and discloses classpath structure on a reachable server.

**Remediation:** Move DevTools settings to `application-dev.yml` and activate them only via `SPRING_PROFILES_ACTIVE=dev`. The main `application.yml` should omit or disable these properties.

**Matrix coverage:** **Not covered** by any matrix row.

---

### LOW-03 · Low · LIKE wildcard characters not escaped in native-query keyword search
**Confidence: Confirmed**

`starter_code/backend/src/main/java/com/globallearning/lms/catalog/repository/CourseRepository.java:34–35`

`LIKE LOWER(CONCAT('%', :keyword, '%'))` — `:keyword` is a bound parameter (safe from SQL injection), but the MySQL LIKE wildcards `%` and `_` within the keyword string are not escaped before substitution. A caller supplying `keyword=%` receives every active course regardless of level or code filter; `keyword=_` returns all one-character-code courses.

**Impact:** Keyword semantics are bypassable; large result sets may cause performance issues. Not exploitable for data exfiltration beyond what `GET /api/courses` already returns.

**Remediation:** Escape `%`, `_`, and `\` in the keyword before passing it to the repository (e.g., `keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")`), and add `ESCAPE '\\'` to the `LIKE` clauses.

**Matrix coverage:** SEC-10 proposes this test but is **Blocked (needs live MySQL)**. It cannot be verified with the mock-only decision currently in force.

---

### LOW-04 · Low · GitHub Actions action references use mutable version tags
**Confidence: Confirmed**

`.github/workflows/ci.yml:17, 21`

`actions/checkout@v3` and `actions/setup-python@v4` are referenced by tag rather than by immutable commit SHA. If either action's tag is force-pushed with malicious content, the next CI run executes the compromised code with repository write access and the full set of GitHub Secrets.

**Impact:** Supply-chain compromise of the CI environment; secrets exfiltration; tampered build artefacts.

**Remediation:** Pin to full commit SHAs, e.g., `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683`.

**Matrix coverage:** **Not covered** by any matrix row.

---

### INFO-01 · Info · Demo Flyway location runs in all profiles — intentional but unguarded
**Confidence: Confirmed**

`starter_code/backend/src/main/resources/application.yml:24–26`

```yaml
flyway:
  locations: classpath:db/migration,classpath:db/demo
```

The `db/demo` location contains `V90__demo_data.sql`, which inserts the prompt-injection course (MED-03), over-capacity enrollments, and an `ALTER TABLE` adding `legacy_sis_code`. These run unconditionally in every profile, including any future staging or production deployment.

**Remediation:** Move `classpath:db/demo` to a separate Spring profile (`application-dev.yml` or a Flyway `spring.flyway.locations` override activated only for development).

**Matrix coverage:** F14 in the strategy notes this fact. **No matrix row** proposes a test that enforces profile-based separation.

---

## Summary table

| ID | Severity | File : Line(s) | Matrix row | Matrix would detect? |
|---|---|---|---|---|
| CRIT-01 | Critical | `db-setup/01-create-users.sql:13,16`; `application.yml:14`; `mcp.json:11` | SEC-08 | Yes — Blocked(Q7) |
| HIGH-01 | High | `SecurityConfig.java:20–23` | SEC-01, SEC-02 | Yes — Not started / Blocked(Q4) |
| HIGH-02 | High | `EnrollmentCreateRequest.java:1–12` | ENR-07 | Yes — Blocked(Q6) |
| HIGH-03 | High | `UserRepository.java:12` | LOGIN-11 | Yes — Blocked(Q2) |
| HIGH-04 | High | `CatalogAuditLogger.java:27` | SEC-05 | Yes — Not started |
| HIGH-05 | High | `mcp.json:5–6` | **None** | **No — gap in matrix** |
| MED-01 | Medium | `V5__create_learners_and_enrollments.sql:12–19` | ENR-06 | Partial — Blocked (needs DB) |
| MED-02 | Medium | `CourseService.java:37,70,90` | **None** | **No — gap in matrix** |
| MED-03 | Medium | `V90__demo_data.sql:33` | RAG-01 (partial); RAG-03 (Unsupported) | Partial — MCP vector not covered |
| MED-04 | Medium | `CourseMapper.java:33,35` | SEC-03 | Yes — Not started |
| MED-05 | Medium | `ci.yml:19–25` | REG-05 | Yes — Not started |
| LOW-01 | Low | `application.yml:12` | **None** | **No — gap in matrix** |
| LOW-02 | Low | `application.yml:5–7` | **None** | **No — gap in matrix** |
| LOW-03 | Low | `CourseRepository.java:34–35` | SEC-10 | Partial — Blocked (needs DB) |
| LOW-04 | Low | `ci.yml:17,21` | **None** | **No — gap in matrix** |
| INFO-01 | Info | `application.yml:24–26` | **None** | **No — gap in matrix** |

---

## What was reviewed

| Artifact | Reviewed |
|---|---|
| `payment-login-java-project` source: `LoginService`, `UserRepository`, `User`, `LoginServiceTest`, `pom.xml` | ✓ |
| `starter_code/backend` source: `SecurityConfig`, `CatalogAuditLogger`, all five controllers, all exception handlers, `CourseUpdateRequest`, `ArchiveRequest`, `EnrollmentCreateRequest`, `CourseMapper`, `CourseService`, `EnrollmentService`, `CourseRepository`, `application.yml` | ✓ |
| `starter_code/backend` migrations/fixtures: `V5__create_learners_and_enrollments.sql`, first 40 lines of `V90__demo_data.sql` | ✓ |
| `starter_code/db-setup/01-create-users.sql` | ✓ |
| `starter_code/.vscode/mcp.json` | ✓ |
| `starter_code/frontend`: `vite.config.js`, `App.jsx`, `api/catalog.js`, `CourseCard.jsx` | ✓ |
| `.github/workflows/ci.yml` | ✓ |
| `docs/test-matrix.md`, `docs/test-strategy.md`, `CLAUDE.md` | ✓ |

## What was not reviewed

- All remaining Flyway migrations (V1–V4, V6+) — referenced but not in the read list
- `starter_code/frontend/src/components/CourseList.jsx`, `LevelFilter.jsx`, `constants.js` — not in the read list; `error` rendering path not fully verified
- `EnrollmentMapper`, `LearnerService`, domain entity classes, `EnrollmentRepository` — not in the read list; `LearnerController.java:31` calls `enrollmentRepository.findByLearnerOrderByCreatedAtDesc()` whose learner-not-found behaviour is unverified
- `node_modules`, `target` — excluded per scope rules
- Remaining 36 lines of `V90__demo_data.sql` — only first 40 lines were read
- Any `.env`, `.env.local`, or other dotfiles not in the specified list

## What cannot be verified statically

- Whether `MessageDigest.isEqual` in `LoginService.java:44` is actually constant-time for all JVM implementations (it is documented as constant-time in Java 6+, but this cannot be confirmed by source reading alone)
- Whether `EnrollmentService.enroll()` genuinely exhibits the race condition (MED-01) in practice — requires a concurrent integration test against a real database
- Whether any downstream log-aggregation system interprets injected log lines (HIGH-04) — requires a running SIEM
- Whether `CourseList.jsx` renders the `error` string as text (safe) or dangerously as HTML — file not read
- Whether `npm audit` advisories in `starter_code/frontend/node_modules` represent exploitable paths — the strategy notes these are uninvestigated

## Matrix coverage gaps — findings the proposed test matrix would not detect

The following findings have **no matrix row** that would catch them:

| Finding | Gap description |
|---|---|
| **HIGH-05** (MCP `npx -y` supply chain) | No SEC-* or any other row covers unpinned tooling-config package execution. Recommend adding a static check: scan `.vscode/mcp.json` for `npx -y` without a pinned version. |
| **MED-02** (hard-coded `"anonymous"` actor) | SEC-01 tests that writes succeed anonymously but does not assert the audit log's actor field. Add an assertion to SEC-05 or a new SEC-11 row that the actor value is not `"anonymous"` once authentication is added. |
| **LOW-01** (`useSSL=false` in JDBC URL) | No row scans `application.yml` for insecure JDBC flags. Can be added to SEC-08's static-scan scope. |
| **LOW-02** (DevTools in committed config) | No row checks for dev-only settings in the main config file. Add to SEC-08 or as a new static check. |
| **LOW-04** (mutable GitHub Actions tags) | No row covers CI supply-chain hygiene. Can be a low-priority static-scan addition. |
| **INFO-01** (demo Flyway in all profiles) | No row asserts profile separation for demo data. The risk is documented in F14 but no test enforces the separation. |
