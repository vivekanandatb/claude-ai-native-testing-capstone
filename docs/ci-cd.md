# CI/CD quality pipeline

Written for: capstone maintainers and reviewers who will read the first CI run and decide whether to merge.

Status: **workflow written and locally validated. No GitHub Actions run exists yet.** Nothing in this document claims a passing run on GitHub. See [reports/ci-local-validation.md](../reports/ci-local-validation.md) for the local evidence and the list of remaining GitHub-side steps.

Workflow file: [.github/workflows/quality.yml](../.github/workflows/quality.yml)

## Triggers

| Event | Jobs that run |
|---|---|
| `push` to `main` | `build-and-unit`, `ui-smoke` |
| `pull_request` (any branch) | `build-and-unit`, `ui-smoke` |
| `workflow_dispatch` | `build-and-unit`, `ui-smoke`, and `rag-safety-eval` (input `run_rag_eval`, default true) |

Note: the repository's default branch is currently `master` (see the git status recorded at the start of the session). A push to `master` does not trigger this workflow. If `master` stays the default, change `branches: [main]` in the workflow or rename the branch. This was not changed without your decision.

Concurrency: pull-request runs for the same ref cancel the earlier run.

## Permissions and secrets

- Top-level `permissions: contents: read`. No job requests write, admin, id-token, or package scopes.
- `persist-credentials: false` on every checkout, so the token is not left in the git config.
- **No secrets are referenced.** The application under test runs locally, uses synthetic data, and the RAG pipeline is stdlib-only with no model API.
- If a model API is later required (for example a Ragas judge), add it as a GitHub Secret and reference it only in the job that needs it. Record the secret name here. Never print it, and never run that job on untrusted pull-request code.
- No deploy, release, or merge step exists. Merge and release stay with human approval.

## Jobs and stages

### Job 1: `build-and-unit` (stages 1-4)

| Step | Command (verified) | Source of verification |
|---|---|---|
| Java runtime | `actions/setup-java` Temurin **21** with Maven cache | Catalog `pom.xml` `java.version` = 21. The baseline ran on JDK 25.0.3-ea; see the caveat below. |
| Build payment/login | `mvn -B -q compile` in `payment-login-java-project` | `reports/baseline-results.md` A |
| Build catalog | `mvn -B -q compile` in `starter_code/backend` | `reports/baseline-results.md` B |
| Unit tests, payment | `mvn -B test` in `payment-login-java-project` | `reports/baseline-results.md` A (now 36 tests) |
| Unit tests, catalog | `mvn -B test` in `starter_code/backend` | `reports/baseline-results.md` B (now 105 tests) |
| Expected-count gate | `python3 -I scripts/ci_check_test_count.py --min 36 …payment…/surefire-reports` and `--min 105 …backend…/surefire-reports` | Fails on zero tests, too few tests, or any failure/error |
| Node runtime | `actions/setup-node` Node **22** | Baseline `node -v` = v22.22.2 |
| Frontend install | `npm ci` in `starter_code/frontend` | Lockfile install; baseline clean-copy `npm ci` |
| Frontend build | `npm run build` in `starter_code/frontend` | `reports/baseline-results.md` C′ |
| Reports | sanitized Surefire XML uploaded as `java-surefire-reports` | Redaction explained below |

### Job 2: `ui-smoke` (stages 1, 5-12)

| Step | Command | Notes |
|---|---|---|
| Node and frontend deps | `npm ci` in `starter_code/frontend` | Needed to start the dev server |
| Pinned Python | `actions/setup-python` **3.14.4** | Verified local interpreter version |
| Test dependencies | `python -m pip install -r requirements-test.txt` | `pytest==9.2.0`, `pytest-playwright==0.10.0`, `playwright==1.65.2` plus transitive pins (all exact) |
| Chromium + OS deps | `python -m playwright install --with-deps chromium` | Installs Chromium and system packages on the runner |
| Start application | `setsid npm run dev -- --host localhost --port 5173 --strictPort` in `starter_code/frontend` | Verified dev command (`baseline` C′). `setsid` gives the server its own process group so cleanup can stop Vite itself. |
| Health wait | Bounded poll (90 attempts, 1 s apart) until `GET http://localhost:5173/` returns HTML containing `id="root"` | Observable endpoint, not a fixed sleep |
| Deterministic tests | `python -m pytest` with `CATALOG_BASE_URL=http://localhost:5173` | 4 tests in `tests/ui/test_catalog_smoke.py`; `/api/courses` is stubbed in the browser |
| Expected-count gate | `python3 -I scripts/ci_check_test_count.py --min 4 test-results/junit-ui.xml` | Runs even after failure (`if: always()`) |
| Stop application | `kill -TERM -- -<pgid>`, then a bounded check that port 5173 stops answering | Runs even after failure |
| Artifacts | `test-results/` (JUnit XML, failure screenshots, failure traces) and the frontend log, all sanitized, uploaded as `ui-smoke-artifacts` | Uploaded even after failure; retention 14 days |

**Why the backend is not started.** The catalog backend cannot start without MySQL (`localhost:3306`). Baseline evidence: `reports/baseline-results.md` section 4, Error 1. Starting MySQL in CI would need the committed development credentials in `db-setup/01-create-users.sql` and `application.yml`, which the repository flags as an open question (`docs/test-strategy.md` Q7). That is a decision for you, not a CI default. The UI tests stub the API instead, so the frontend is exercised without the backend.

### Job 3: `rag-safety-eval` (stages 13-15, manual)

Runs only on `workflow_dispatch` with `run_rag_eval` true.

| Step | Command | Gate |
|---|---|---|
| Index | `python3 -I scripts/build_index.py` | Must exit 0 |
| Required cases | `python3 -I scripts/query_rag.py --batch rag/eval/test_questions.jsonl --out …` | Exit 1 if any expectation is not met |
| Extra cases | `python3 -I scripts/query_rag.py --batch rag/eval/extra_questions.jsonl --out …` | Exit 1 if any expectation is not met |
| Safety gate | `python3 -I scripts/rag_safety_gate.py --min-refusals 3 …` | Fails if any `REFUSED_*` case (injection or secret request) regressed, or fewer than 3 such cases exist (so deleting safety cases cannot pass silently) |
| CSV | `python3 -I scripts/rag_results_to_csv.py …` | Export for human review |
| Upload | `rag-safety-reports`: JSONL and CSV, retention 30 days | Uploaded even after failure |

**Ragas is not run.** The `rag-evaluation` skill describes Ragas metrics, but Ragas needs a judge model, and no judge model is approved or configured. The `requirements-rag.txt` file records that the pipeline is stdlib-only. The job above is the deterministic safety evaluation. Adding Ragas needs three decisions: which judge model, who approves hosted use (it sends chunk text to an external provider and may cost money), and where its API key is stored (a GitHub Secret). Stage 17 is therefore not triggered.

**Human review.** Every RAG record carries `human_review: required`. A green job does not mean an answer was reviewed. The pipeline summary says no human has reviewed the answers (`reports/rag-pipeline-summary.md`).

## Artifacts and sanitization

- Every upload is preceded by `scripts/ci_sanitize_reports.py`. It rewrites text files (XML, TXT, LOG, JSON, JSONL, CSV, MD, YAML) and redacts:
  - the Spring Boot line `Using generated security password: <value>`, which appears in Surefire output embedded in the XML (confirmed in `starter_code/backend/target/surefire-reports`);
  - `password`, `secret`, `token`, and `api key` assignments;
  - token-shaped strings (`sk-…`, `AKIA…`, `ghp_…`, `eyJ…`).
- The sanitizer prints counts only, never values.
- Binary files (traces `trace.zip`, PNG screenshots) are not rewritten. The UI suite uses only synthetic stubbed data, so traces contain no real credentials. Keep it that way: do not add tests that log in with real or shared accounts.

## Local reproduction

```
# Java
cd payment-login-java-project && mvn -B test
cd starter_code/backend && mvn -B test

# Frontend
cd starter_code/frontend && npm ci && npm run build && npm run dev -- --host localhost --port 5173 --strictPort

# UI smoke (in another shell, with the frontend running)
python3 -m venv .venv && . .venv/bin/activate && pip install -r requirements-test.txt
python -m playwright install --with-deps chromium
CATALOG_BASE_URL=http://localhost:5173 python -m pytest

# RAG safety
python3 -I scripts/build_index.py
python3 -I scripts/query_rag.py --batch rag/eval/test_questions.jsonl --out ci-artifacts/rag/test_questions.results.jsonl
```

## Known limits and risks

- **Java runtime.** The workflow pins Java 21, the declared catalog target. The baseline ran on JDK 25.0.3-ea. The first CI run on 21 is the first real evidence for that runtime.
- **Actions are pinned by major version** (`@v4`, `@v5`), not by commit SHA. I could not verify commit SHAs with a trustworthy tool here, and an incorrect SHA would break every run. Pin to SHAs once they can be checked (for example with `gh api` after `gh` is installed).
- **`pip install` is not hash-locked.** `requirements-test.txt` pins exact versions but not hashes. Adding `--require-hashes` needs hash generation, which is a follow-up.
- **`npm ci` runs dependency install scripts.** Not changed; `--ignore-scripts` was not verified against this project's build.
- **Existing `.github/workflows/ci.yml`** still exists. It triggers on `master` and only sets up Python 3.10 with no tests. It is untracked and was not modified. It overlaps with `quality.yml`; remove it after your approval.
- **`node_modules` in `starter_code/frontend` is macOS-built** (see the baseline). CI uses a fresh `npm ci`, so it does not depend on that directory. The directory is now git-ignored.
- **The repository files are untracked.** `git status` shows the new workflow and the rest of the project as untracked. The workflow cannot run on GitHub until these files are committed and pushed. That needs your approval.
- **Playwright deprecation warning.** `pytest-playwright` 0.10.0 emits a `PytestRemovedIn10Warning` from its own `base_url` fixture. It is not a test failure. Revisit when the plugin is updated.
- **Maven exit codes.** The baseline found that a backend start can print `BUILD SUCCESS` while the application fails. CI does not start the backend, so this does not affect the gates, but the count gate reads the XML reports rather than trusting exit codes alone.

## Remaining GitHub-side steps (manual)

1. Commit and push the untracked files, including `.github/workflows/quality.yml`. Pushing needs your explicit approval.
2. Make `main` the default branch, or change the trigger, so pushes to the current default branch run the workflow.
3. Confirm the repository's Actions settings allow workflows to run. Under Settings → Actions → General, keep the default token permissions at read-only if the option is offered.
4. Open a pull request, and confirm both jobs run. Look at the first `ui-smoke` run's log for the health-wait line and the pytest summary.
5. Use `workflow_dispatch` once to run the RAG safety job and check that the CSV and JSONL artifacts appear.
6. Branch protection on `main`: require the `Build and unit tests` and `Deterministic UI smoke tests` checks before merging, and require a human approval. Merge and release stay with people.
