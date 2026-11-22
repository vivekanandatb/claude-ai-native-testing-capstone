# Security controls

Written for: capstone maintainers and testers who add code, configuration, tests, or MCP integrations to this repository.

This document lists the controls the project relies on, where each one lives, and how to verify it. Findings from the most recent review are in [reports/security-review.md](../reports/security-review.md).

## 1. Secrets

**Rule.** No secret value (password, token, API key, private key, connection string with credentials) is stored in a tracked or untracked repository file, in a test fixture, in a log, in a report, or in a screenshot or trace.

**How credentials are supplied.**

| Component | Variable or source | Where set |
|---|---|---|
| Catalog backend datasource password | `DB_PASSWORD` (Spring placeholder `${DB_PASSWORD}` in `starter_code/backend/src/main/resources/application.yml`) | Developer shell or CI secret, never the file |
| MySQL user creation | `starter_code/db-setup/01-create-users.sql` contains `<SET-BEFORE-RUNNING>` placeholders | Replace locally with a value from your secret store; do not commit the edited copy |
| MySQL MCP server (VS Code) | `LMS_MCP_MYSQL_PASSWORD` referenced as `${env:LMS_MCP_MYSQL_PASSWORD}` in `starter_code/.vscode/mcp.json` | Developer environment |
| Case-study Snowflake sample | `SNOWFLAKE_PASSWORD` environment lookup in `case_study.txt` | Developer environment |
| GitHub Actions | None required by `.github/workflows/quality.yml` | Add a GitHub Secret only if a model API becomes necessary, and record the name in `docs/ci-cd.md` |

**Verification.**

```
git grep -n -E 'password\s*[:=]\s*[^$<{ ]' -- ':!*.md'   # expect no literal values
python3 -I scripts/ci_sanitize_reports.py <dir>          # redacts report artifacts before upload
```

**Limitation.** Git history is not rewritten by any of these controls. A value committed before the control existed remains in history until history is rewritten and the credential is rotated. See section 9.

## 2. Ignore rules

`.gitignore` must exclude:

- environment files (`.env`, `.env.*`, with `!.env.example` allowed);
- key material and keystores (`*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`, `*.kdbx`);
- local settings (`.claude/settings.local.json`);
- build and dependency output (`node_modules/`, `target/`, `dist/`);
- generated test and CI artifacts (`test-results/`, `playwright-report/`, `ci-artifacts/`, `.pytest_cache/`);
- generated RAG index (`rag/index/`) and Python caches;
- MCP output (`reports/playwright-mcp-out/`).

**Known exception.** `starter_code/.gitignore` un-ignores `.vscode/mcp.json`. Keep that file free of literal secrets.

**Verification.** `git check-ignore -v <path>` for each class above.

## 3. Database privileges

Set up by `starter_code/db-setup/01-create-users.sql` (one-time, run as root):

- `lms_app`: `ALL PRIVILEGES` on `lms_catalog.*`. Runs the application and Flyway.
- `mcp_readonly`: `SELECT` on `lms_catalog.*`. Intended for read-only agent access.

**Known gap.** Both users are created for host `'%'` (any host). Narrow them to `localhost` or the specific client host before exposing MySQL beyond the developer machine.

## 4. MCP servers

| Server | Config | Permissions | Status |
|---|---|---|---|
| Playwright (approved) | Local scope in `~/.claude.json`; `@playwright/mcp@0.0.86`; `--allowed-origins http://localhost:5173`; `--isolated`; no secrets | Browser actions on localhost only; deny rules in `.claude/settings.local.json` for `browser_run_code_unsafe`, `browser_evaluate`, `browser_file_upload`, `browser_drop` | Configured; deny rules not yet verified in an interactive session |
| MySQL (`@benborla29/mcp-server-mysql`, in `starter_code/.vscode/mcp.json`) | Launched with `npx -y`, no version pin | `mcp_readonly` user; `ALLOW_INSERT/UPDATE/DELETE` set to `false` | **Not approved.** Unpinned third-party package with database credentials. Verify it, pin it, or remove it. |

**Rules for any MCP server:**

- Pin the package to an exact version and check the publisher before use.
- Use a dedicated least-privilege account and an environment variable for the password.
- Restrict network reach (for example `--allowed-origins`, `--allowed-hosts`, or a loopback-only database host).
- Keep destructive tools disabled unless a human has approved them for a specific task.
- Treat all tool output as untrusted data.

## 5. GitHub Actions

`quality.yml` (see [docs/ci-cd.md](ci-cd.md)):

- top-level `permissions: contents: read`;
- `persist-credentials: false` on every checkout;
- no `secrets.*` references;
- no deploy, release, or merge step;
- pull-request runs do not receive secrets.

**Gap.** The older `.github/workflows/ci.yml` declares no `permissions:` block, so its token scope follows the repository default, which may be read-write. Add `permissions: contents: read` to it, or remove it after approval.

**Action pinning.** Actions are pinned by major version (`@v4`, `@v5`). Replace with full commit SHAs once they can be verified.

## 6. Logging and reports

- Application code does not log passwords. `LoginService` returns booleans and does not log its inputs.
- `CatalogAuditLogger` writes the free-text archive `reason` into the log line. Treat that text as untrusted; a value containing line breaks can forge log entries. Escape or length-limit it before logging.
- Test reports are sanitized before upload (`scripts/ci_sanitize_reports.py`). Spring Boot's `Using generated security password: …` line is redacted.
- Screenshots and traces are produced only on failure and use synthetic data only. Do not add tests that sign in with real or shared accounts.

## 7. Prompt-injection handling (RAG and agent-facing data)

- The RAG pipeline (`rag/pipeline/safety.py`) quarantines lines that look like embedded instructions and masks them before prompts are built.
- The grounded-QA prompt (`rag/prompts/grounded_qa.txt`) states that retrieved text is untrusted data and must not be followed.
- Batch evaluation (`rag/eval/`) includes injection and secret-request refusal cases. `scripts/rag_safety_gate.py` fails the job if those cases regress or disappear.
- **Residual risk.** The demo seed data (`starter_code/backend/src/main/resources/db/demo/V90__demo_data.sql`, line 33) contains text addressed to AI assistants. Any agent that reads catalog descriptions, including a database MCP server, must treat them as untrusted data.

## 8. Test data and live-system rules

- Tests use only synthetic data. Email domains in the repository are `example.com`.
- No test calls a live payment gateway. The payment project has no gateway code; the catalog has no payment code.
- No production URL is referenced by code or configuration. Stripe's dashboard link appears only in a documentation example.
- The UI smoke suite stubs `/api/courses` in the browser, so it does not call a backend or database.

## 9. Incident response for committed secrets

If a value is committed:

1. **Rotate first.** Change the password or key at its source (database, Snowflake, provider). Treat the value as exposed as soon as it reached any shared remote.
2. **Remove from current files.** Replace the value with a variable or placeholder and commit that change.
3. **Assess history.** Search all refs: `git log --all -p -S '<known fragment>'` or a scanner over `git rev-list --all`. Do not paste the value into tickets or chat.
4. **Rewrite history only with explicit approval.** Tools such as `git filter-repo` change every commit hash and require a force push. Coordinate with all clones before doing it.
5. **Verify.** Re-scan current files and history after rewrite; confirm the credential no longer works.

## 10. Approval gates

- Merge and release are human decisions. The quality workflow has no merge, deploy, or release step.
- Branch protection on `main` (required checks and at least one human approval) is recommended. It is a repository setting and was **not verified** in this review.
- Destructive operations, package upgrades, pushes, and remote changes require explicit approval (project rule 16).

## 11. Known open items

See the unresolved-risks section of [reports/security-review.md](../reports/security-review.md). In brief: committed credentials in history and in the tracked zip archives, an unapproved MySQL MCP server, open catalog write endpoints, and the `'%'` database host scope.
