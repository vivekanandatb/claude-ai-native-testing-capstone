# Repository Inspection Report

Method: read-only inspection (file reads, `grep`, `unzip -l` / `unzip -p`). Nothing was installed, started, built, or executed. No tests were run. No application files were modified.
Credential values found in files are intentionally **not** reproduced here (CLAUDE.md rule 5); only their location and key names are given.

Confidence scale: **confirmed** (seen directly in a file), **inferred** (reasonable reading, not verified), **unknown** (cannot be established from the repo).

---

## 0. Headline finding

The brief describes a "login and payment" application. **The primary application in this repository has neither.** It is a *Learning Catalog (LMS)* in `starter_code/`. A separate, tiny login/payment Java starter exists only as a zip at the repo root (`payment-login-java-project.zip`), where login is explicitly unimplemented and there is no payment code at all.

| Item | Finding | Evidence | Confidence |
|---|---|---|---|
| Domain | Course catalogue / enrolment service, not login/payment | `starter_code/README.md` ("Learning Catalog"); controllers listed in §9–10 | confirmed |
| Login/payment project | Separate zip, login stubbed, no payment | `payment-login-java-project.zip` → `LoginService.java` (`throw new UnsupportedOperationException`), `Main.java` prints "Login functionality is intentionally not implemented." | confirmed |

---

## 1. Repository root and folder structure

- Git root: `/home/labuser/Desktop/git-demo` (confirmed: `.git` present; branch `TestBranchV`, created from `master`).
- Root contains an unrelated mix of training artifacts: `Case_Study1`, `case_study.txt`, `case_study2`, `casestudy.txt` (text case studies), `Dockerfile` + `app.py` (Flask hello-world), `bottleneck.sql`, `snowflake.sql`, shell scripts (`configk8`, `setup_k8`, `terraform`, `micronaut`, `zig`), `piepline` (Jenkinsfile text), small `*.txt` files, two zips, `.github/workflows/ci.yml`. Confidence: confirmed.
- `CLAUDE.md` and `reports/` (this file) were added by this exercise at the root.
- `starter_code/` was extracted from `D4 - Starter Code.zip` (uncommitted, untracked):

```
starter_code/
  AGENTS.md  README.md  .gitignore  agent-audit.log
  backend/    pom.xml  src/main/{java,resources}  src/test/java  target/ (build output, pre-existing)
  frontend/   package.json  package-lock.json  vite.config.js  index.html  src/  node_modules/ (pre-bundled)
  db-setup/   01-create-users.sql
  scripts/    audit-log.js  format-files.sh  format-files.ps1
  .github/    instructions/react.instructions.md  skills/{security-review,create-readme,rest-endpoint-builder}
  .vscode/    mcp.json
```
Confidence: confirmed (file listing).

## 2. Programming languages

- Java (backend) — `starter_code/backend/src/**/*.java`. confirmed
- JavaScript/JSX (frontend) — `starter_code/frontend/src/*.jsx|js`. confirmed
- SQL (Flyway migrations) — `backend/src/main/resources/db/{migration,demo}`. confirmed
- Node.js script (agent audit hook) — `scripts/audit-log.js`. confirmed
- Root-level extras: Python (`app.py`, SmartEPM zip), Bash, Zig, Terraform/K8s scripts. confirmed (not part of the app under test)

## 3. Application framework

- Spring Boot **3.5.4** (Web, Data JPA, Validation, Security, Flyway; MySQL driver; DevTools). Source: `backend/pom.xml` `<parent>` `spring-boot-starter-parent` version `3.5.4`. confirmed
- Frontend: React `^18.3.1` + Vite `^5.4.2` + `@vitejs/plugin-react`. Source: `frontend/package.json`. confirmed

## 4. Java / runtime version

- Project target: Java **21**. Source: `backend/pom.xml` `<java.version>21</java.version>`. confirmed
- Conflicting docs: `README.md` says "Java 17+", `AGENTS.md` doesn't state one. The pom is authoritative. confirmed
- Installed in this environment: OpenJDK `25.0.3-ea` (`java -version`), Node `v22.22.2`. confirmed. Whether Java 25 EA compiles/runs this project: **unknown**.
- Payment zip targets Java 17 (`maven.compiler.release` 17). confirmed

## 5. Build system

- Backend: **Maven** (`backend/pom.xml`). No `mvnw`/Gradle files found in `backend/`. confirmed
- **`mvn` is not installed** in this environment (`which` returned only java, node, npm). confirmed
- Frontend: npm scripts in `frontend/package.json` (`dev`, `build`, `preview`). confirmed
- Payment zip: Maven with `maven-surefire-plugin 3.2.5`. confirmed

## 6. Dependency manifests

- `starter_code/backend/pom.xml`, `starter_code/frontend/package.json`, `starter_code/frontend/package-lock.json`. confirmed
- Payment zip: `pom.xml` (JUnit Jupiter 5.10.2 only). SmartEPM zip: `requirements.txt` (streamlit, pandas, anthropic, openpyxl, pytest). confirmed
- Root `ci.yml` sets up Python 3.10 but no `requirements.txt` exists at root. confirmed

## 7. Application start command

- Backend: `cd starter_code/backend && mvn spring-boot:run`. Source: `starter_code/README.md` "Quick start" and `AGENTS.md`. **Documented, not executed.** Confidence: confirmed (documented) / unverified (works).
- Frontend: `cd starter_code/frontend && npm install && npm run dev`. Source: README + `package.json` `scripts.dev` = `vite`. confirmed (documented); not executed.
- Backend cannot start with the default config unless MySQL is reachable (see §8, §14). confirmed from `application.yml`.

## 8. Default port and configuration

- Backend: **8080**. `application.yml` key `server.port: 8080`. confirmed
- Frontend dev server: **5173**. `vite.config.js` `server.port: 5173`. confirmed
- Vite proxies `/api` → `http://localhost:8080` (`vite.config.js` `server.proxy`). confirmed
- Database: MySQL on **3306** (`spring.datasource.url`). A separate MCP config points to **3307** (`.vscode/mcp.json` `MYSQL_PORT`). confirmed
- Docs conflict: `README.md`/`AGENTS.md` say H2 in-memory; `application.yml` and `pom.xml` (mysql-connector-j, flyway-mysql; no H2 dependency) say MySQL. The config files are authoritative. confirmed

## 9. Login route and implementation

- **No login route in the catalog app.** Controllers found: `CourseController` (`/api/courses`), `EnrollmentController` (`/api/enrollments`), `LearnerController` (`/api/learners`). No auth/login controller. confirmed (`backend/src/main/java/.../web/`)
- `SecurityConfig.java`: `csrf.disable()`; `requestMatchers("/api/courses/**").permitAll()`; `anyRequest().permitAll()`. All requests are unauthenticated. confirmed
- Frontend has no login page or form (`App.jsx`, `components/*`). confirmed
- Payment zip: `LoginService.login()` throws `UnsupportedOperationException`; `LoginServiceTest` has four tests that are `assertTrue(true)` placeholders. No HTTP layer. confirmed

## 10. Payment route and implementation

- **No payment code, route, or UI in `starter_code/`.** Controllers listed in §9; grep for payment-related terms in `backend/src/main`, `frontend/src`, `scripts` matched only unrelated content (`application.yml` datasource password key, `SecurityConfig`, CSS/JSX "course-card"). confirmed
- **No payment code in the payment zip** either (files: `Main`, `auth/*` only). confirmed

## 11. Verified UI pages and navigation flows

- Single page, no router: `App.jsx` renders header "Course catalogue", a `LevelFilter` and a `CourseList`. confirmed
- `LevelFilter.jsx`: `role="group"` `aria-label="Filter by level"`; buttons "All levels", "Foundation", "Intermediate", "Advanced" (labels from `constants.js`). confirmed
- `CourseList.jsx` states: "Loading the catalogue…", error message ending "Check that the API is running on port 8080.", empty-state "No courses match this filter. Clear the filter to see the full catalogue.", otherwise a `<ul>` of `CourseCard` (code, level, title, "N credits"). confirmed
- The frontend calls only `GET /api/courses` (+ `?level=`); `fetchCourseByCode` exists but is not used by any component. `keyword`/`q` is supported by the API client but no UI input exists. confirmed
- No `data-testid` attributes found. confirmed
- Rendered behavior in a browser: **unknown** (not run).

## 12. Existing test frameworks and commands

- Backend: JUnit 5 + Spring Boot Test + MockMvc + Mockito (`@WebMvcTest`, `@MockitoBean`), AssertJ; `spring-security-test` dependency. Tests: `CourseControllerTest` (4 tests), `CourseMapperTest`, `LegacyCourseCodeParserTest`. confirmed
- Command (documented): `cd starter_code/backend && mvn test` (README, AGENTS.md). **Not run.**
- `CourseControllerTest` is a slice test with `addFilters = false` and a mocked service, so it likely needs no database. inferred
- `returnsCourseDetailByCode` calls `perform(get(...))` twice and the second asserts `$.capacity == 30` against a `Course` built in the first stub. Whether this passes depends on `Course` constructor semantics I did not read. **unknown, do not assume it passes or fails.**
- No `src/test/resources`; no test profile. confirmed
- Frontend: no test framework, no test script, no Playwright/Vitest/Jest in `package.json`. confirmed
- Payment zip: JUnit 5, `mvn test` implied by surefire; tests are placeholders. confirmed/inferred
- SmartEPM zip: `pytest` (`tests/test_variance.py`). confirmed (not part of target app)

## 13. Test accounts and synthetic fixtures

- **No application login accounts** in the catalog app. confirmed
- Synthetic learners: `L-00001`..`L-00010` (migration `V5__create_learners_and_enrollments.sql`), `L-00011`+ (demo seed `V90__demo_data.sql`); names "Learner One…", emails `learnerN@example.com`. confirmed
- Seeded courses: 8 in `V2__seed_courses.sql`, plus demo seed adding course `LMS9001` and 40 retired/archived courses. confirmed
- Demo seed is deliberately messy: duplicate ACTIVE enrolments, a course over capacity, an unmapped column `legacy_sis_code`, and an unreviewed partner description (per the file's own comments). confirmed
- Payment zip: synthetic user `demo.user` with a plaintext password defined in `UserRepository.java` constructor (comment: "Synthetic demo account only"). Value not reproduced. confirmed

## 14. Configuration files

- `backend/src/main/resources/application.yml` (datasource, JPA `ddl-auto: validate`, `flyway.locations: classpath:db/migration,classpath:db/demo`, `server.port`, logging level for `CATALOG_AUDIT`). confirmed
- **Notable:** the default Flyway config includes `db/demo`, so the messy demo data loads in every environment using this config. confirmed
- `frontend/vite.config.js`, `package.json`; `.vscode/mcp.json`; `db-setup/01-create-users.sql`; `.gitignore`. confirmed
- Root: `Dockerfile` (python:3.9-slim, Flask app, unrelated to catalog). confirmed
- Spring profiles / `application-*.yml`: none found. confirmed

## 15. Environment variables

- Backend `application.yml` uses **no** `${ENV}` placeholders; all values are literal. confirmed
- `.vscode/mcp.json` sets `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASS`, `MYSQL_DB`, and `ALLOW_{INSERT,UPDATE,DELETE}_OPERATION` as literals. confirmed
- Root `Dockerfile` sets `PYTHONDONTWRITEBYTECODE`, `PYTHONUNBUFFERED`, `APP_HOME`. confirmed
- SmartEPM zip `.env.example`: `ANTHROPIC_API_KEY` (empty), `CLAUDE_MODEL`. confirmed
- No GitHub Secrets referenced anywhere. confirmed

## 16. External service calls

- MySQL via JDBC at `localhost:3306` (`application.yml`). confirmed
- MySQL MCP server launched with `npx -y @benborla29/mcp-server-mysql` (unpinned, downloaded at run time) against `127.0.0.1:3307` (`.vscode/mcp.json`). confirmed
- No outbound HTTP calls from backend or frontend code (only same-origin `/api` fetches). confirmed for files read; I did not read every service class line by line. inferred for completeness
- SmartEPM zip: Anthropic API usage per README/requirements. inferred (agent code not read)

## 17. Real payment provider

- **None referenced** in `starter_code/` or the payment zip. confirmed (no payment code; no gateway SDK in `pom.xml` / `package.json`)
- Root-level text case studies may mention payments/billing conceptually; not checked in detail and not application code. unknown

## 18. Security concerns

1. **Plaintext credentials committed:** datasource username/password in `application.yml`; app and read-only DB users with passwords in `db-setup/01-create-users.sql`; read-only DB password in `.vscode/mcp.json`. Look like local dev placeholders, but they violate rules 5–6 as a pattern. confirmed
2. **No authentication or authorization:** `anyRequest().permitAll()` plus `csrf.disable()` means `PUT /api/courses/{code}`, `POST .../archive|deactivate|reactivate`, and `POST /api/enrollments` are open to anyone. confirmed
3. **Audit `actor` is unverifiable:** `CatalogAuditLogger` logs an `actor` string, but there is no authentication to supply a trustworthy value. Where `actor` comes from in services: not traced. inferred / partly unknown
4. **Log injection / data in logs:** `recordCourseArchived` logs free-text `reason` from the request body. inferred risk (needs sanitisation check)
5. **Prompt-injection content in data:** `V90__demo_data.sql` course `LMS9001` has a description containing instructions aimed at AI assistants. It is untrusted input, was not followed, and should be treated as a fixture for testing safe handling of untrusted content. confirmed
6. **Unpinned `npx -y` MCP server** with DB access: supply-chain and trust risk. confirmed
7. **PII-shaped data:** learners have name/email columns; current seed values are synthetic (`example.com`). confirmed
8. **Payment data:** no payment data handled anywhere in the app. confirmed
9. **`agent-audit.log`** (inside `starter_code/`, gitignored there) records past tool calls including SQL via an MCP server on a `learners` table. It is evidence of a prior session, contents not otherwise analysed. confirmed
10. Documentation contradicts configuration (H2 vs MySQL; Java 17 vs 21), which could mislead test setup. confirmed

## 19. CI/CD configuration

- Only workflow: `/.github/workflows/ci.yml` ("CI/CD pipeline"), triggers on push and pull_request to `master`. Steps: `actions/checkout@v3`, `actions/setup-python@v4` (Python 3.10). **It builds and tests nothing**, and is unrelated to the Java/React app. confirmed
- `starter_code/.github/` contains instructions and "skills" only, **no workflows**. confirmed
- `piepline` (root) is a Jenkinsfile-style text that checks out a GitHub URL and echoes a message; no build/test. confirmed
- Hook configuration that would invoke `scripts/audit-log.js`: not found. unknown

## 20. Missing information (cannot be established from the repo)

- Whether `mvn test` passes (Maven not installed; nothing executed).
- Whether the app starts: needs MySQL 8.4 with the DB/users created; availability here is unknown.
- Whether Java 25 EA works with the pom's Java 21 target.
- Which application the capstone is meant to target (catalog vs. payment zip), and whether login/payment must be built or only tested.
- Intended login/payment business rules, routes, selectors, accounts: none exist to inspect.
- `Course` entity constructor/default semantics (affects one existing test's expectation).
- Real source of `actor` in audit calls.
- Exact `CourseService`/`EnrollmentService` business rules (capacity, duplicate enrolment) beyond exception class names.
- Browser-rendered UI behavior.
- Whether the root `.github` CI is meant to be extended for this project.
