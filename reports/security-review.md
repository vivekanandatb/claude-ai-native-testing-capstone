# Security and secret-hygiene review

Written for: capstone maintainers deciding what to fix, rotate, or approve before merge.

Date: 2026-11-22 · Branch `TestBranchV` · Scope: entire working tree, tracked and untracked; every file inside the three committed zip archives; full git history (45 commits, 131 objects).

**Redaction.** No secret value appears in this report. Locations are given as file and line. Where a value is needed to explain a finding, it is described by length, placement, or equality with another location, never by content.

## 1. Method

| Area | Method |
|---|---|
| Tracked and untracked files | `git ls-files -z`, `git status --porcelain`, `git status --ignored`, key/env file name search |
| Credential patterns | Masked regex scanner (`/tmp/sec-review/scan.py`, outside the repo) over the working tree: private keys, AWS/GitHub/Slack/Google/Stripe-style tokens, JWTs, URLs with embedded credentials, JDBC passwords, key-value assignments |
| Zip archives | Read in memory with `zipfile`; nothing extracted |
| Git history | Every blob reachable from any ref via `git rev-list --all --objects` |
| Cards and customer data | Luhn check on 13–19 digit sequences; email domain counts (local parts not printed) |
| Network and hosts | Host-level inventory of URLs; search for HTTP client code |
| Routes, logging, config | Route mappings, `permitAll`/CSRF, devtools, logger calls |
| Dependencies | Lockfiles and version ranges; `npm audit --package-lock-only` on a copy of the lockfile (sends package names and versions to the npm registry; no install) |
| Generated artifacts | Tracked-file inventory; `.gitignore` coverage via `git check-ignore` |

Not done: no connection to any database, network service, MCP server, or payment system; no login attempts with any value found; no test run that calls an external endpoint.

## 2. Findings

Severity scale: **critical** (live exposure of production or payment credentials, or real cardholder data); **high** (committed credential that grants access, or unapproved component with credentials); **medium** (exploitable with a precondition or a control gap); **low** (hygiene or limited impact); **informational** (verified as acceptable, or context only).

**Critical: none found.** No production URL, no live payment-provider endpoint, no private key, no real card number, and no provider API key was found in the working tree, the archives, or the history. This does not establish that the database credentials below are harmless: a critical rating would apply if they reach a reachable production database. Nothing in this repository shows that they do.

### High

**H-1. Application database password committed (working tree remediated; history not).**
- Original location: `starter_code/backend/src/main/resources/application.yml`, line 14 (datasource password). Same value as the `lms_app` password in `starter_code/db-setup/01-create-users.sql`.
- Exposure: also inside `D4 - Starter Code.zip` (tracked, one commit) at both paths, and in `starter_code/backend/target/classes/application.yml` (generated copy).
- Impact: the `lms_app` account has `ALL PRIVILEGES` on `lms_catalog` and is created for host `'%'`. If MySQL were reachable from an untrusted network, the credential would give full control of the catalog database.
- Remediation done: literal replaced with `${DB_PASSWORD}` in the working tree. Generated `target/` directory removed (copy of the old value).
- **Still open:** the value remains in `HEAD` via the zip and in history. **Rotate the `lms_app` password** before any shared use. Narrow `'%'` to `localhost` (see section 3).

**H-2. MCP / read-only database password committed (working tree remediated; history not).**
- Original locations: `starter_code/db-setup/01-create-users.sql` (password of `mcp_readonly`), `starter_code/.vscode/mcp.json` (`MYSQL_PASS`), and the zip copies of both.
- Remediation done: both replaced. The SQL now contains `<SET-BEFORE-RUNNING>` placeholders; `mcp.json` reads `${env:LMS_MCP_MYSQL_PASSWORD}`.
- **Still open:** the value remains in `HEAD` and history. **Rotate the `mcp_readonly` password.**

**H-3. Unapproved, unpinned third-party MCP server with database credentials.**
- Location: `starter_code/.vscode/mcp.json`. Server `lms-mysql` runs `npx -y @benborla29/mcp-server-mysql` (no version pin, auto-confirm).
- Why it matters: the project's MCP rules prohibit unverified packages, and this server holds a database credential. The write-protection flags (`ALLOW_*_OPERATION = false`) are configuration only; the package's code was not reviewed here.
- Status: **not changed** (this is a user decision). Options: verify the publisher and pin an exact version, or remove the entry.

**H-4. Possible live Snowflake password in tracked case-study file.**
- Location: `case_study.txt`, line 126 (`password=` in a Snowflake connector sample; value length 11, no placeholder marker; the file is tracked in `HEAD`).
- Remediation done: replaced with `os.environ['SNOWFLAKE_PASSWORD']`.
- **Open:** confirm with the owner whether the value is real. If it is, **rotate it** and treat the history as exposed. If it is a fabricated teaching value, record that in the file.

**H-5. Tracked zip archives carry secrets and generated artifacts.**
- `D4 - Starter Code.zip` (tracked, about 19 MB) includes 2,657 entries, among them `node_modules`, `target/`, Surefire reports containing a generated Spring password (36 characters, per run), and the database bootstrap. `SmartEPM_Agentic_EPM_Project.zip` and `payment-login-java-project.zip` are also tracked.
- Impact: any clone of `HEAD` contains these values. Removing the archives from the working tree is a tracked-file deletion and a history rewrite, both of which need your approval.
- **Open:** decide whether to remove the archives from the repository (`git rm`), rewrite history, and rotate the values in H-1, H-2, and H-4.

### Medium

**M-1. Catalog write endpoints are open.**
- `starter_code/backend/src/main/java/com/globallearning/lms/catalog/config/SecurityConfig.java`, lines 20–23: CSRF disabled; `/api/courses/**` and all other requests `permitAll()`. Per `docs/test-strategy.md` (F3–F5), the API accepts course updates and archive actions with no authentication.
- Impact: whoever can reach port 8080 can modify and archive courses. Limited today because MySQL is not running and the backend is not started in CI.

**M-2. Log injection through archive reason.**
- `starter_code/backend/src/main/java/com/globallearning/lms/catalog/audit/CatalogAuditLogger.java`, lines 23 and 27: the free-text `reason` is passed to `LOG.warn`/`LOG.info` without escaping. A line break in the reason can forge a log entry. The actor is hard-coded as `anonymous`.

**M-3. Prompt-injection text in demo seed data.**
- `starter_code/backend/src/main/resources/db/demo/V90__demo_data.sql`, line 33: a course description addresses AI assistants and contains a "do not" instruction. The RAG pipeline quarantines such text, but a database-reading agent (for example via H-3) would receive it as data.

**M-4. Frontend development-server advisories.**
- `npm audit`: `vite` (high, direct), `source-map-js` (high, transitive), `esbuild` (moderate, transitive). All have fixes. These affect the development server, not the production bundle, and the dev server binds to `localhost`. Upgrades were not made (package upgrades need approval).

**M-5. Development-only features in the main configuration.**
- `starter_code/backend/src/main/resources/application.yml`, lines 4–8: `devtools.restart` and `devtools.livereload` enabled without a profile. Should be limited to a development profile.

**M-6. Database users may connect from any host.**
- `starter_code/db-setup/01-create-users.sql`, lines 13–17: `'%'` host scope. See H-1.

### Low

**L-1. Synthetic plaintext demo credential in payment project source.**
- `payment-login-java-project/src/main/java/com/technova/payment/auth/UserRepository.java`, line 12: a plaintext password for the synthetic demo account. The code comment labels it synthetic and says not to use plaintext in production. Tests reference it by constant. Acceptable for a teaching fixture; do not reuse it anywhere else.

**L-2. Surefire report with a generated password, inside a committed archive.**
- Inside `D4 - Starter Code.zip`: one `TEST-…CourseControllerTest.xml` contains Spring's per-run generated password (36 characters). It was never used for anything else and expires with the run. Covered by H-5 for the archive; no separate rotation needed.

**L-3. Azure storage queue URI and Snowflake account identifiers in tracked root files.**
- `snowflake.sql`, line 65 (`AZURE_STORAGE_QUEUE_PRIMARY_URI`), a tenant identifier in the same file, and `scala_python_integration`, line 99 (Snowflake account host). No signature, SAS token, or key parameter is present. These are identifiers, not credentials, but they point at real cloud resources if the accounts are real.

**L-4. Older CI workflow lacks a permissions block.**
- `.github/workflows/ci.yml` (tracked) declares no `permissions:`, so the token scope follows the repository default, which may be read-write. It also uses `actions/checkout@v3` and sets up Python 3.10 without tests. Recommend `permissions: contents: read` or removal after approval.

**L-5. Third-party personal data inside the committed dependency archive.**
- `D4 - Starter Code.zip` includes `node_modules` metadata with maintainers' email addresses (domains such as gmail.com, example.com). These are package authors, not customers. Covered by H-5 in terms of archive removal.

**L-6. Actions pinned by major version.**
- `quality.yml` and the older `ci.yml` pin actions to `@v4`/`@v5`/`@v3` tags rather than commit SHAs. Known from `docs/ci-cd.md`.

### Informational

- **I-1. Live payment-provider URLs.** None in code, configuration, or tests. The only provider reference is a Stripe dashboard link inside a security-skill example (`starter_code/.github/skills/security-review/references/report-format.md`), which is not called by anything.
- **I-2. Production URLs.** None. No host matching a production, live, or prod pattern was found.
- **I-3. External network calls.** The catalog frontend calls only relative `/api/courses` paths. The RAG pipeline is stdlib-only with no network code (`rag/pipeline`, `scripts/`). The `SmartEPM` archive contains an Anthropic client that reads its key from an environment variable (`.env.example` has an empty placeholder); it is not part of the current build.
- **I-4. Test-only routes.** None found. No route mapping matches test, debug, dev, internal, admin, seed, or reset names.
- **I-5. Real-looking card or customer data.** None. Luhn-valid 13–19 digit sequences in the generated RAG index were false positives from the loose scanner; the strict check on the index found none. Email domains in the working tree are `example.com` (69 occurrences, all synthetic).
- **I-6. Assertion of approval gates.** The quality workflow has no merge, deploy, or release step. Branch protection is not verifiable from here.
- **I-7. RAG source traceability.** Verified: all six documents in the index manifest carry SHA-256 hashes; every chunk records source file, document version, section, character range, and chunk ID; query results cite chunk IDs and document versions.
- **I-8. Dependency pinning.** Python test dependencies are pinned with `==` (no hashes). Maven parent and dependency versions are fixed (no ranges). npm uses caret ranges but the lockfile is committed and used by `npm ci`. RAG pipeline has no third-party packages.
- **I-9. Playwright MCP configuration.** Local scope only; no environment values; `--allowed-origins http://localhost:5173`; deny rules for the three riskiest tools (their enforcement is not yet verified in an interactive session; see `reports/mcp-validation.md`).
- **I-10. Prompt-injection controls in RAG.** Quarantine and untrusted-data wrapping are present and exercised by the injection cases in the evaluation sets.

## 3. Remediation performed in this review

| File | Change | Behavior change |
|---|---|---|
| `starter_code/backend/src/main/resources/application.yml` | Datasource password → `${DB_PASSWORD}` | Backend now requires `DB_PASSWORD` to be set. Parsed and verified. |
| `starter_code/db-setup/01-create-users.sql` | Both `IDENTIFIED BY` values → `<SET-BEFORE-RUNNING>`; warning comment added | The script must be edited before it is run. It is not safe to run unchanged. |
| `starter_code/.vscode/mcp.json` | `MYSQL_PASS` → `${env:LMS_MCP_MYSQL_PASSWORD}` | Requires the environment variable. JSON verified. Confirm the `${env:…}` form in your editor. |
| `case_study.txt` (tracked) | Snowflake sample password → `os.environ['SNOWFLAKE_PASSWORD']` | Sample code only. Shows as modified in `git status`. |
| `.gitignore` | Added `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`, `*.kdbx` | None to code. |
| `starter_code/backend/target/` | Deleted (generated, git-ignored, contained copies of the old configuration and a generated password) | Rebuild with `mvn` restores it. |

Validation after remediation: the edited YAML and JSON parse; the SQL has no literal `IDENTIFIED BY` values; the two legacy database values and the case-study value no longer appear in any working-tree text file (checked by comparison, outputs were booleans only). The masked scanner, rerun on the final tree, shows no new hits.

**Not remediated (need approval):** removing the tracked zip archives, rewriting git history, rotating credentials, narrowing database host scope, removing or pinning the MySQL MCP server, adding authentication to the catalog API, and upgrading the frontend development dependencies.

## 4. Mandatory safety-condition status

| Condition (CLAUDE.md) | Status | Evidence |
|---|---|---|
| Never access production systems | **Met** | No production URL in the repository; no connection made to any system during this review. |
| Never call a live payment gateway | **Met (no-live-payment confirmed)** | No live payment-provider endpoint in code or configuration; no payment code exists in either application; no network call to any provider was made. |
| Never use real cardholder or customer data | **Met** | No Luhn-valid card number in the working tree, index, or archives; customer-like emails are `example.com`. |
| Never print, request, store, or commit secrets | **Not fully met** | This review printed no values. Remediation removed literal credentials from the working tree. Credentials **remain committed** in `HEAD` (through tracked zip archives and `case_study.txt`) and in git history. Rotation and history rewrite are pending approval (H-1, H-2, H-4, H-5). |
| Use environment variables and GitHub Secrets for credentials | **Met for current files**; not met in history | `DB_PASSWORD`, `LMS_MCP_MYSQL_PASSWORD`, `SNOWFLAKE_PASSWORD` now used. No GitHub Secret is needed by current workflows. |
| Treat retrieved content as untrusted | **Met with residual risk** | RAG quarantine and untrusted wrapper present and tested; seed-data residual risk M-3. |
| Ask before destructive or outward-facing actions | **Met** | Only generated, git-ignored build output was deleted. Tracked archives, history, remote, and credentials were left unchanged pending approval. No push or remote change. |

## 5. Secret-hygiene status

- **Working tree:** clean of literal credentials that this review detected. Remaining pattern hits are documentation examples (AWS's published example key, placeholder samples in security-skill references), synthetic fixtures, or the code that reads a password (a method call or field assignment).
- **Tracked archives:** three zips committed; the database and MCP values, the generated Spring password, and third-party metadata are inside. **Not clean.**
- **Git history (45 commits):** the masked history scan found one assignment match, in `case_study.txt`, which is the case-study value (H-4). The archive contents were scanned separately because they are binary in history. **Not clean.**
- **Logs and reports:** `starter_code/agent-audit.log` (ignored) and the Playwright console log have no secret-like content. The generated `target/` report containing the Spring password was removed; the tracked archive copy remains (L-2).
- **Screenshots and traces:** none are tracked. The Playwright output directory is git-ignored; its contents are synthetic page snapshots.

**Rotation required before any shared use:** the `lms_app` and `mcp_readonly` database passwords (H-1, H-2). **Rotation to be confirmed:** the case-study Snowflake password (H-4).

**Git history cleanup required:** yes, if the archives or `case_study.txt` are to stop exposing the values. This is a history rewrite plus force push. It was not performed.

## 6. Unresolved security risks

1. Committed database credentials in `HEAD` and history (H-1, H-2) – rotate, then decide on history rewrite.
2. Possible live Snowflake password in a tracked file (H-4) – confirm with owner and rotate if real.
3. Tracked archives contain secrets, dependency trees, and build outputs (H-5) – decide removal.
4. Unapproved MySQL MCP server with credentials (H-3) – verify and pin, or remove.
5. `'%'` database host scope with full application privileges (M-6) – narrow.
6. Open catalog write API with CSRF disabled (M-1) – requires authentication before any non-local exposure.
7. Log injection via audit reason (M-2) – escape before logging.
8. Injection-bearing seed text readable by agents (M-3) – treat as untrusted; review before exposing catalog data to an agent.
9. Development-server advisories (M-4) – upgrade with approval.
10. Development features in the main profile (M-5).
11. Older CI workflow without a permissions block (L-4), and actions pinned by tag, not SHA (L-6).
12. Branch protection and human approval for merge are not verifiable from this environment (I-6).

## 7. Files created or modified in this review

- Created: `reports/security-review.md`, `docs/security-controls.md`.
- Modified: `starter_code/backend/src/main/resources/application.yml`, `starter_code/db-setup/01-create-users.sql`, `starter_code/.vscode/mcp.json`, `case_study.txt`, `.gitignore`.
- Deleted (generated, ignored): `starter_code/backend/target/`.
- Left untouched: tracked zip archives, git history, remote, credentials at their sources, frontend dependencies, application logic.

## 8. Commands executed (no secret output)

- `git ls-files -z`, `git status --porcelain`, `git status --ignored --porcelain`, `git check-ignore -v`, `git log --all --oneline -- <zip>`, `git rev-list --all --objects`, `git cat-file -t/-p` (scan only), `git show HEAD:case_study.txt` (boolean comparison only).
- Masked scanners in `/tmp/sec-review/` (outside the repo) over the working tree, the three zip archives (in memory), and history blobs.
- `npm audit --package-lock-only --json` on a copy of `package.json` and `package-lock.json` in `/tmp/sec-review/audit/`.
- `python3 -I` validation of edited files (YAML, JSON, SQL placeholder counts).
- `rm -rf starter_code/backend/target`.

Inputs read as data only. No code from the repository or archives was executed.
