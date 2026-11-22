# Baseline Results

Date: 2026-11-20 · Branch: `TestBranchV` · Based only on repository evidence (see `reports/repository-inspection.md`).
No application source files were modified. No real credentials, personal data, or payment actions were used. No payment was submitted (none exists).

## Overall status: PARTIAL — not a clean baseline

| Component | Build | Existing tests | Start | Overall |
|---|---|---|---|---|
| A. Payment/login Java project (`payment-login-java-project/`) | PASS | PASS 4/4 (placeholders only) | Runs as a console `main`; no server | PASS (with caveat) |
| B. Catalog backend (`starter_code/backend`) | PASS (compiled by `mvn test`) | PASS 10/10 | **FAIL** (MySQL unreachable) | **FAIL to start** |
| C. Catalog frontend, original tree (`starter_code/frontend`) | **FAIL** | none exist | **FAIL** | **FAIL** (wrong-platform `node_modules`) |
| C′. Catalog frontend, clean copy in `/tmp/fe-baseline` (`npm ci`) | PASS | none exist | PASS (port 5173) | PASS |

Login and payment pages/routes: **do not exist** in either application (details below).

## 1. Environment versions (observed)

| Tool | Version | Source command |
|---|---|---|
| OS | Linux 7.0.0-1005-gcp, x86_64 | `uname -m` |
| Java / javac | OpenJDK 25.0.3-ea (2026-04-21) | `java -version`, `javac -version` |
| Maven | 3.9.12 (installed this session via apt, with approval) | `mvn -v` |
| Node / npm | v22.22.2 / 10.9.7 | `node -v`, `npm -v` |
| Vite | 5.4.21 | `npm run build` output |
| Spring Boot | 3.5.4 | `mvn` output banner |
| MySQL / mysqld / docker | **not installed** | `which` found nothing |

Project targets: catalog pom `java.version` = 21 (ran on Java 25, compiled with `release 21`); payment pom `maven.compiler.release` = 17. Both built on Java 25 without error.

## 2. Build and test commands and results

### A. Payment/login Java project
- Command: `cd payment-login-java-project && mvn -B test` → `BUILD SUCCESS`, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.
- Compile: `mvn -B -q compile` → exit 0.
- Caveat: all 4 tests in `LoginServiceTest` are `assertTrue(true)` placeholders. `LoginService.login()` throws `UnsupportedOperationException`. The 4 passes show nothing about login behavior.
- "Start": `java -cp target/classes com.technova.payment.Main` (no documented start command exists; this is the only entry point) printed:
  ```
  Payment application starter project
  Login functionality is intentionally not implemented.
  ```

### B. Catalog backend
- Documented test command: `cd starter_code/backend && mvn -B test`
- Result: `BUILD SUCCESS`, `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`
  - `LegacyCourseCodeParserTest` 4, `CourseControllerTest` 4, `CourseMapperTest` 2.
- Resolves the earlier "unknown" about `returnsCourseDetailByCode`: it passes.
- Observation: the test log prints a framework-generated throwaway security password (value omitted here). Tests use `@WebMvcTest` with mocks, so no database is needed.
- Warnings (non-failing): Mockito self-attaching agent and dynamic agent loading under JDK 25.

### C. Catalog frontend
- Documented commands: `npm run build`, `npm run dev` (`package.json`).
- No test script or framework exists.

## 3. Start commands, URLs, and verified routes

### Backend (B) — FAILED
- Command: `cd starter_code/backend && mvn -B spring-boot:run` (documented in `README.md`).
- Configured port: 8080 (`application.yml` `server.port`). **Actual listening port: none.** `ss -ltn` showed nothing on 8080 after the run.
- Result: the application context failed to start. Maven nevertheless printed `BUILD SUCCESS` / process exit 0, so the exit code is **not** a reliable success signal; the listener check and the log are the ground truth.
- No backend routes could be verified live.

### Frontend original tree (C) — FAILED
- Commands: `npm run build`, `npm run dev` in `starter_code/frontend`.

### Frontend clean copy (C′) — PASSED
- Setup: copied `package.json`, `package-lock.json`, `vite.config.js`, `index.html`, `src/` to `/tmp/fe-baseline`, then `npm ci` (exit 0; installs exactly the lockfile versions, no upgrades).
- `npm run build` → `vite v5.4.21 … ✓ 36 modules transformed … ✓ built in 1.18s` (exit 0).
- `npm run dev` → `VITE v5.4.21 ready in 280 ms`, `Local: http://localhost:5173/`.
- Actual listening port confirmed with `ss`: `127.0.0.1:5173` (node process).
- **Verified local URL: http://localhost:5173/** (frontend only; backend API at 8080 was not running).

| Request (read-only GET) | Result | Meaning |
|---|---|---|
| `GET /` | 200 text/html, `<title>Global Learning — Course catalogue</title>`, `id="root"` | Real page: single-page catalogue |
| `GET /login` | 200 text/html | Vite SPA fallback serving the same shell; **no login page/route exists** (no router or login component in `src/`) |
| `GET /payment` | 200 text/html | Same SPA fallback; **no payment page/route exists** |
| `GET /api/courses` (Vite proxy → 8080) | **500 text/plain** | Proxy target (backend) is down |

Note: `/login` and `/payment` returning 200 is a dev-server artifact, not evidence of those pages. Body equality with `/` was not byte-compared (inferred from SPA fallback behavior).

## 4. Errors encountered

### Error 1 — Backend cannot start: MySQL unreachable
- Original failure (`/tmp/backend-start.log`):
  `Unable to obtain connection from database: Communications link failure` … `Caused by: java.net.ConnectException: Connection refused`, raised by Flyway (`flywayInitializer`) while building `entityManagerFactory`; `HikariPool-1 - Starting...` precedes it.
- Root cause (confirmed): `application.yml` points to MySQL at `localhost:3306`; nothing listens on 3306 and no MySQL binary is installed here. The README/AGENTS.md claim of an H2 in-memory DB is wrong (no H2 dependency exists).
- Correction applied: **none.** The only fixes are installing and bootstrapping MySQL 8.4 (`db-setup/01-create-users.sql`, which creates users with passwords), or changing application config/dependencies (e.g., an H2 profile). Both go beyond "smallest safe correction" and need your approval.
- Final result: unresolved; backend not running.

### Error 2 — Frontend original tree: `ERR_MODULE_NOT_FOUND`
- Original failure: `Cannot find module '…/frontend/node_modules/dist/node/cli.js' imported from …/node_modules/.bin/vite`.
- Cause 1: the zip stored `node_modules/.bin/vite` as a regular file, not a symlink, so its relative import resolves against `.bin/`.
- Retried by calling `node node_modules/vite/bin/vite.js build` directly (no file changes). New failure: `Cannot find module @rollup/rollup-linux-x64-gnu`.
- Cause 2 (confirmed): the bundled `node_modules` was installed on macOS (`node_modules/@rollup` contains only `rollup-darwin-arm64`; this machine is Linux x86_64).
- Correction applied: a clean `npm ci` in a **temporary copy** at `/tmp/fe-baseline`, so `starter_code/frontend/node_modules` was left untouched. Final result: build and dev server **pass** in the copy.
- Recommended permanent fix (needs approval, deletes a gitignored directory): in `starter_code/frontend`, run `npm ci`.
- `npm ci` also reported known-vulnerability advisories (`npm audit`); not investigated, no upgrades made.

### Error 3 (process hygiene) — leftover dev server
- After the first stop attempt, port 5173 was still listening because only the npm wrapper was signaled. I found the vite process and stopped it; final `ss` check shows 5173 and 8080 closed.

## 5. Relevant terminal output (excerpts)

```
# A. payment project
[INFO] Running com.technova.payment.auth.LoginServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

# B. catalog backend tests
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

# B. catalog backend start
HikariPool-1 - Starting...
Unable to obtain connection from database: Communications link failure
Caused by: java.net.ConnectException: Connection refused
(no listener on 8080)

# C. original frontend
Error [ERR_MODULE_NOT_FOUND]: Cannot find module '.../node_modules/dist/node/cli.js' imported from .../node_modules/.bin/vite
Error: Cannot find module @rollup/rollup-linux-x64-gnu

# C'. clean copy
dist/index.html 0.42 kB | dist/assets/index-*.css 1.37 kB | dist/assets/index-*.js 144.83 kB
✓ built in 1.18s
VITE v5.4.21 ready in 280 ms   ➜ Local: http://localhost:5173/
GET / -> 200 | GET /login -> 200 | GET /payment -> 200 | GET /api/courses -> 500
```

## 6. Cleanup and repository state
- All servers stopped; `ss` shows nothing on 5173 or 8080.
- Created: `payment-login-java-project/` (extracted from the zip, plus Maven `target/`), `reports/`, `CLAUDE.md`, and the temp copy `/tmp/fe-baseline` (outside the repo, can be deleted).
- `git status`: only untracked `CLAUDE.md`, `payment-login-java-project/`, `reports/`, `starter_code/`. No tracked file was modified. Nothing committed or pushed.

## 7. Pass/fail summary

- PASS: Maven/Java/Node toolchain available; payment project build + 4 placeholder tests; catalog backend 10/10 tests; frontend build + dev server in a clean install.
- FAIL: catalog backend start (no MySQL); frontend build in the original tree (macOS `node_modules`).
- NOT VERIFIABLE: live backend routes, any login or payment behavior (neither exists), rendered UI in a browser.
