# CI local validation: quality pipeline

Written for: capstone maintainers reviewing whether the workflow is ready to push.

Date: 2026-11-22 · Branch `TestBranchV` · Workflow: [.github/workflows/quality.yml](../.github/workflows/quality.yml) · Documentation: [docs/ci-cd.md](../docs/ci-cd.md)

**Status: NOT VERIFIED ON GITHUB.** No GitHub Actions run exists. Everything below was run locally, on this Linux machine, outside Actions. Local results cannot prove the workflow runs on `ubuntu-latest`.

## 1. Workflow structure

| Job | Triggers | Stages |
|---|---|---|
| `build-and-unit` | push to `main`, pull request, manual | 1 checkout, 2 Java 21, 3 build (payment compile, catalog compile, frontend build), 4 unit tests with count gate, reports upload |
| `ui-smoke` | push to `main`, pull request, manual | 1 checkout, 5 Python 3.14.4, 6 pinned deps, 7 Chromium, 8 start frontend, 9 health wait, 10 deterministic UI tests, 11 count gate, 12 artifacts (even after failure) |
| `rag-safety-eval` | manual only (`run_rag_eval`, default true) | 13 RAG batch cases, 14 safety-regression gate, 15 CSV and JSONL upload |

Permissions: top-level `contents: read`. No secrets are referenced. No deploy, merge, or release step.

## 2. Actual test and build commands (local runs)

| Command | Result |
|---|---|
| `cd payment-login-java-project && mvn -B test` | exit 0, 3 suites, **36 tests, 0 failures, 0 errors, 0 skipped** (Surefire XML) |
| `cd starter_code/backend && mvn -B test` | exit 0, 10 suites, **105 tests, 0 failures, 0 errors, 0 skipped** (Surefire XML) |
| `npm ci && npm run build` in a clean copy of `starter_code/frontend` (`/tmp/fe-mcp`) | exit 0, `✓ built in 1.24s` |
| `npm run dev -- --host localhost --port 5173 --strictPort` (via `setsid`) | listening; `GET /` returns HTML containing `id="root"` after 2 s |
| `python -m pytest` (pinned venv, `CATALOG_BASE_URL=http://localhost:5173`) | **4 passed**, 0 failed, 1 plugin deprecation warning |
| `python -m pytest` with the app **stopped** (`CATALOG_BASE_URL=http://localhost:5999`) | **4 failed** (as intended); trace and screenshot artifacts were written for each |
| `python3 -I scripts/build_index.py` | exit 0 |
| `python3 -I scripts/query_rag.py --batch rag/eval/test_questions.jsonl` | `expectations met: 8/8`, exit 0 |
| `python3 -I scripts/query_rag.py --batch rag/eval/extra_questions.jsonl` | `expectations met: 5/5`, exit 0 |
| `python3 -I scripts/rag_safety_gate.py --min-refusals 3 …` | `safety refusal cases=3 … regressed=0`, PASS |
| `python3 -I scripts/rag_results_to_csv.py …` (both files) | 8 and 5 rows written |

The Java counts come from `mvn -B test` runs in this session, against the unchanged existing suites; the workflow only reads their Surefire reports.

## 3. Negative tests (gates must fail when they should)

| Check | Expected | Observed |
|---|---|---|
| Count gate with `--min 999` against 105 real tests | fail | `FAIL: collected 105 tests, expected at least 999`, exit 1 |
| Count gate against a report with `tests="0"` | fail | `FAIL: zero tests were collected`, exit 1 |
| RAG batch with case Q7 (injection refusal) relabelled as `ANSWERED` (simulated regression) | fail | `expectations met: 7/8`, exit 1 |
| Safety gate with `--min-refusals 50` (simulates deleted safety cases) | fail | `FAIL: fewer safety refusal cases than expected`, exit 1 |
| UI suite with the application stopped | fail | 4 failed |

## 4. Sanitization check

Surefire XML in `starter_code/backend/target/surefire-reports` contains three lines `Using generated security password: <value>`. The source files were not changed.

On a copy (`/tmp/san`), `scripts/ci_sanitize_reports.py` produced:
- zero remaining password-shaped values (`grep` count 0);
- three `generated security password: [REDACTED]` markers;
- the source files still contained the values (3 matches), confirming the copy was the only file changed.

The sanitizer prints counts only. Its counts overstate the number of values by about two, because the generic `password:` pattern matches the already-redacted line too. The only key redacted was `password`.

## 5. Static inspection of the workflow

| Area | Check | Result |
|---|---|---|
| YAML syntax | `yaml.safe_load` | OK; jobs `build-and-unit`, `ui-smoke`, `rag-safety-eval`; triggers `push`, `pull_request`, `workflow_dispatch` |
| Linter | `actionlint` 1.7.12 with `shellcheck` 0.11.0 on the `run:` blocks | Clean after one fix (an unused loop variable in the stop step) |
| Quoting | `"$RUNNER_TEMP/…"`, `"$!"`, `"-$pid"`, `'id="root"'` | Quoted; `kill -TERM -- "-$pid"` uses `--` so the negative PGID is not read as an option |
| Paths | every `working-directory`, `cp`, `path:` against the repo layout | All exist; `ci-artifacts/{java,ui,rag}` match the upload paths |
| Background process | `setsid npm run dev … &`, PID written to `$RUNNER_TEMP/frontend-dev.pid` | Local simulation: health reached, then stop by process group; port 5173 closed; no Vite process left |
| Health wait | bounded loop, 90 × 1 s, `curl --max-time 5`, requires `id="root"` | Local run: healthy after 2 s; no fixed sleep |
| Cleanup | `if: always()` on stop, count gate, and artifact steps | Present |
| Secrets | `grep secrets\.` | No references. The only matches are comments that say no secrets are used. |
| Expressions | `${{ github.ref }}`, `${{ github.event_name … }}`, `inputs.run_rag_eval` | Valid |
| Artifact sanitization | every upload is preceded by `ci_sanitize_reports.py` | Present in all three jobs |

## 6. Findings fixed during validation

1. `actionlint` SC2034: unused `i` in the frontend stop loop. Changed to `_`.
2. Coverage gap: `.gitignore` did not ignore `node_modules/` or `target/`, so a push would have committed them. Added `node_modules/`, `target/`, `dist/`, `test-results/`, `playwright-report/`, `.pytest_cache/`, `ci-artifacts/`, and `.claude/settings.local.json`.
3. Consistency: UI and RAG jobs now use the pinned `python`; the Java job keeps system `python3` because it sets up no Python and runs stdlib-only scripts.

## 7. Limits of this validation

- **Runtime differences.** Local Java is JDK 25.0.3-ea; the workflow uses Temurin 21. Local Python is 3.14.4 from the system; the runner's `setup-python` 3.14.4 is not yet confirmed to exist in its manifest.
- **Actions not executed.** `actions/checkout@v4`, `setup-java@v4`, `setup-node@v4`, `setup-python@v5`, and `upload-artifact@v4` were not run here.
- **`--with-deps`** installs system packages on the runner. It ran locally here, which installed apt packages on this machine.
- **`setsid` on Ubuntu runners** was exercised here on Linux but not in Actions.
- **The repo's frontend copy** used for the UI run was `/tmp/fe-mcp`, not `starter_code/frontend`, because the repo copy has macOS `node_modules`. CI uses `npm ci` in `starter_code/frontend`, which was not run in place.
- **Backend not started.** The catalog backend needs MySQL; UI tests stub `/api/courses`. Backend behavior is covered only by its unit and `@WebMvcTest` tests.
- **Ragas not run.** Deterministic RAG safety cases only.
- **Pinned by tag, not SHA.** Actions are pinned to major versions. SHAs were not verified.
- **Untracked files.** Everything is untracked; the workflow cannot run on GitHub until the files are committed and pushed.
- **Duplicate workflow.** `.github/workflows/ci.yml` (untracked, `master`, Python 3.10 only) still exists and was not changed.
- **Branch name.** Triggers use `main`; the repository's current default is `master`, so pushes to `master` will not run `push`-triggered jobs.

## 8. Test totals

| Suite | Total | Passed | Failed | Where |
|---|---|---|---|---|
| Payment/login Java unit | 36 | 36 | 0 | Local run, this report |
| Catalog backend Java unit | 105 | 105 | 0 | Local run, this report |
| Deterministic UI smoke | 4 | 4 | 0 | Local run, this report |
| UI smoke negative (app stopped) | 4 | 0 | 4 | Local, expected failures |
| RAG required cases | 8 | 8 | 0 | Local batch |
| RAG extra cases | 5 | 5 | 0 | Local batch |
| RAG safety regression (simulated) | 8 | 7 | 1 | Local, expected failure |
| GitHub Actions runs | 0 | 0 | 0 | **Not run** |

## 9. Files created or modified in this stage

- Created: `.github/workflows/quality.yml`, `docs/ci-cd.md`, `reports/ci-local-validation.md`, `pytest.ini`, `requirements-test.txt`, `tests/ui/test_catalog_smoke.py`, `scripts/ci_check_test_count.py`, `scripts/ci_sanitize_reports.py`, `scripts/rag_safety_gate.py`, `scripts/rag_results_to_csv.py`.
- Modified: `.gitignore`.
- Not modified: any application source, the existing `.github/workflows/ci.yml`, `starter_code/frontend/node_modules`.

## 10. Remaining GitHub-side actions (need your approval where marked)

1. **Approval needed:** commit and push the untracked project (including `.github/workflows/quality.yml`).
2. Confirm the default branch and the trigger branch (`main` or `master`).
3. Open a pull request and confirm `Build and unit tests` and `Deterministic UI smoke tests` run. Check the health-wait line and the pytest summary in the log.
4. Run `workflow_dispatch` once to confirm `rag-safety-reports` (CSV and JSONL) appears.
5. Replace tag pins with SHA pins after they are verified (`gh api` once the GitHub CLI is installed and authenticated).
6. Set branch protection on `main`: require both checks above and a human approval before merge.
7. Decide whether to remove the older `.github/workflows/ci.yml`.

## 11. Repository connection

The GitHub CLI (`gh`) is **not installed**, so I could not show remote status or create anything on GitHub. The repository has an `origin` remote, `https://github.com/sharmaanku/git-demo.git`. No push, branch creation, or remote change was made.
