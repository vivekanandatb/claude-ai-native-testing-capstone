# Agent Orchestration Report

Date: 2026-11-22 · Branch `TestBranchV` · Author of this consolidation: the main Claude session (not one of the three agents).
Raw prompts and outputs are saved in `reports/agent-orchestration-evidence/`. Nothing in this report was approved by a human yet (see §7).

## 1. Agents, tasks, and permissions

| Agent | Definition | Task given | Output |
|---|---|---|---|
| `test-analyst` | `.claude/agents/test-analyst.md` | Review `docs/test-matrix.md` against real source; find wrong evidence, overclaims, missing scenarios, unknowns | **Partial** (see §5) |
| `security-reviewer` | `.claude/agents/security-reviewer.md` | Review the matrix's security coverage and the current application statically; say whether the matrix would detect each finding | Complete (16 findings) |
| `browser-test-author` | `.claude/agents/browser-test-author.md` | Inspect the frontend source; check matrix §7 and RAG-02; propose verified UI tests and locators | Complete |

**Tool scope.** All three agent files request `tools: Read, Grep, Glob` (read and search only: no Edit, Write, or Bash). When I loaded each agent in a headless session, Claude Code reported its tool list as **`['Read']` only**: this build exposes no `Grep` or `Glob`. So the agents could not search; I gave each an explicit list of files to read. None of them could edit files or run commands, and no repository file was changed by any agent run (`git status` showed only my own untracked directories).

How they ran: `claude -p "<task>" --agent <name>` in headless mode, in parallel, with a 900-second cap, from `/tmp/orch/run.sh` (not kept in the repo). The model for those runs was the default configured for headless sessions.

## 2. Findings

### 2.1 security-reviewer (16 findings; line numbers re-verified by me)
| ID | Severity (agent) | Finding | Verified |
|---|---|---|---|
| CRIT-01 | Critical | DB credentials committed in `application.yml`, `db-setup/01-create-users.sql`, `.vscode/mcp.json` (values not reproduced) | Yes |
| HIGH-01 | High | Catalog API unauthenticated; CSRF disabled (`SecurityConfig.java:20-23`) | Yes |
| HIGH-02 | High | `EnrollmentCreateRequest` has no validation constraints | Yes (agent cited `EnrollmentController.java:28`; confirmed) |
| HIGH-03 | High | Plaintext password in the `UserRepository` fixture (`:12` in the agent's text) | Yes (line 12) |
| HIGH-04 | High | Log injection via archive `reason` (`CatalogAuditLogger.java:27`) | Yes |
| HIGH-05 | High | MCP config runs unpinned `npx -y @benborla29/mcp-server-mysql` with DB credentials (`mcp.json:5-6`) | Yes |
| MED-01 | Medium | No unique constraint on (course, learner) in V5 | Yes |
| MED-02 | Medium | Audit actor hard-coded `"anonymous"` | Yes (lines 37, 70, **91**; the agent said 90) |
| MED-03 | Medium | Prompt-injection text in demo seed data, loaded by Flyway in all profiles | Yes (`V90…sql:33`) |
| MED-04 | Medium | Detail endpoint exposes `instructorEmail` and `archiveReason` | Yes (`CourseMapper.java` lines **32**, 35; the agent said 33) |
| MED-05 | Medium | CI runs no tests | Yes |
| LOW-01…04, INFO-01 | Low/Info | JDBC `useSSL=false`; DevTools in committed config; unescaped LIKE wildcards; mutable Action tags (`ci.yml` lines 17 and **20**; the agent said 21); demo Flyway location unconditional | Yes |

Agent's matrix-gap list (findings the matrix would not detect): HIGH-05, MED-02, LOW-01, LOW-02, LOW-04, INFO-01. I accept all six as real gaps.

### 2.2 test-analyst (only Parts 4–7 captured)
- **CAT-02 "Implemented-passing" is suspect** (the agent could not read `Course.java`). **Resolved by me:** `Course.java:67` sets `capacity = 30` by default, and the existing suite passed 10/10 when I ran it (Stage 4). The matrix row is correct.
- Missing scenarios recommended (MS-01…MS-08): write endpoints on an unknown code → 404; enrollment `CourseNotFoundException` via the second `@RestControllerAdvice`; `instructorEmail` optional (no `@NotNull`, confirmed at `CourseUpdateRequest.java:33-35`); case-insensitive code lookup (needs DB); enrollment with null fields may produce a 500 (unobservable under mocks); learner with zero enrollments → `[]`; **`login()` with U+00A0** (`String.isBlank()` does not treat NBSP as blank, so the guard is bypassed but the compare still rejects); deactivate returns `active=false`.
- Correctly labelled unsupported: `UNSUP-SES-*`, `UNSUP-PAY-*`, `RAG-03…05`.
- Its unknowns U-01…U-09 are mostly closed by files I read: `Course.java`, `CourseRepository.java` (bound parameters), `LearnerService.java`, the CI workflow, and `V90`.

### 2.3 browser-test-author
- **Login UI tests impossible; payment UI tests unsupported** (no UI, route, or mock). Agrees with the inspection.
- Element inventory E-01…E-18, all with file:line evidence; **no `data-testid` anywhere**; locators by role, label, and text only.
- All matrix §7 and RAG-02 claims confirmed against source. No corrections needed.
- Ten proposed tests (UI-01…UI-09, RAG-02) written in Python pytest-playwright, plus UI-10 as a build check.
- Gaps: G-01 no `aria-pressed`; G-02 `formatCredits` defined but unused (renders "1 credits"); G-03 no `aria-live`; G-05 UI-10 is a build gate, not a browser test; G-06 UI-06 threading risk; G-07 no search UI although the API client supports `q`.
- Note: `main.jsx:7` uses React StrictMode, so a **dev server** fires effects twice; request counting must tolerate that.

## 3. Disagreements

| # | Between | Disagreement | My resolution and reasoning |
|---|---|---|---|
| D-1 | test-analyst vs. matrix | `CAT-02` claimed suspect | Resolved in favor of the matrix: `Course.java:67` plus a passing run (evidence in §2.2) |
| D-2 | security-reviewer vs. my earlier inspection | **CRIT-01 Critical** for committed credentials vs. my earlier note that they look like local dev placeholders | Both partly right. The values are localhost dev defaults in a training repo, but committed plaintext credentials plus an MCP config that uses them is a real pattern. I record it as **High**, not Critical, until someone confirms whether they are ever reused. **Human decision needed.** |
| D-3 | security-reviewer vs. matrix | **HIGH-03** (plaintext fixture password) rated High vs. the matrix's P1 `LOGIN-11` | The agent itself says "fixture-only, blast radius low". I record it as **Medium (fixture)**, High if the pattern reaches real storage. |
| D-4 | browser-test-author vs. itself | Its UI-06 uses `threading.Event.wait()` inside a route handler, which it also flags as a possible deadlock (G-06), yet its UI-02 uses the safer "hold the route, fulfill later" pattern | Use **hold-and-fulfill-later** for UI-06 too. Do not block inside a handler. |
| D-5 | browser-test-author vs. user decision | It proposes Python pytest-playwright; the user chose **Java for browser tests** (decision of 2026-11-22: "Mixed") | The proposals are kept as designs and **translated to Playwright for Java**. Locators, stubbed data, and expected text carry over unchanged. |
| D-6 | security-reviewer vs. test plan | The matrix would **characterize** current insecure behavior (open endpoints, log injection, public email). A green test could look like endorsement | Include them only as clearly named characterization tests (`…Characterization…`), listed in `reports/defects.md`, to be flipped when fixed. Never described as passing security. |
| D-7 | Agents vs. instructions | The analyst and security reviewer cite the matrix's "known-failing" static scans (`SEC-08`) as intended; adding them as ordinary tests would make the suite fail | Keep them out of the stable suite's totals; run them separately as tagged known-defect checks and report their failures openly. |

## 4. Matrix and strategy updates implied

Add (not yet written to `docs/test-matrix.md`): MS-01…MS-08 from the analyst; security gaps `SEC-11` unpinned `npx -y` in tooling config, `SEC-12` hard-coded audit actor, `SEC-13` JDBC without TLS, `SEC-14` DevTools in committed config, `SEC-15` mutable CI action tags, `SEC-16` demo Flyway location in all profiles. Matrix updates need a human to accept them first.

## 5. Missing evidence and limits

- **test-analyst output is incomplete.** It starts at "Continuing the report"; Parts 1–3 (its numbered findings F-01…F-08, including a "count error" in §1 I could not locate) were not captured. I cannot cite F-02…F-08. Rerunning with a narrower scope would recover them.
- **No agent could search**; each read only the files I listed. They did not read `Course.java` or the other files outside their lists, so claims in those areas are theirs, not independently checked, unless I verified them (§2.1, §2.2).
- **Static review only.** Nothing was executed by the agents. All "would the matrix detect it?" judgments are reasoned, not run.
- **Agents have the same model family as me** and may share blind spots. This is not independent human review.
- **My own slip:** while verifying the security reviewer's citations I ran a `grep` that printed the synthetic fixture password to this session's output. It is the documented synthetic demo value, but it breaks CLAUDE.md rule 5. I have not repeated it, and I checked that the saved evidence files do not contain it.

## 6. Consolidated decision

| # | Decision | Basis |
|---|---|---|
| 1 | **Languages: Java for application, API, and browser tests; Python only for the RAG pipeline.** | User decision, 2026-11-22 |
| 2 | **Java unit tests** (JUnit 5) for the login library and `EnrollmentService`/`CourseService` rules with Mockito; add MS-07 (U+00A0), boundary inputs, log capture, and a secret scan of the payment project | Matrix `LOGIN-08`, `ENR-03…05`, `SEC-06`, `SEC-07`, analyst MS-07 |
| 3 | **Java API tests** with `@WebMvcTest` and mocks only: error contracts, validation boundaries, optional email, learner empty list, exposure, and the cross-handler 404 | Matrix `CAT-05…08`, `ENR-01/02`, `SEC-03/04`; analyst MS-01…03, 06, 08 |
| 4 | **Characterization tests** for open endpoints (real filter chain) and log injection, named as such; defects recorded, never described as passing security | D-6; `SEC-01`, `SEC-05` |
| 5 | **Browser tests in Playwright for Java**: UI-01…UI-09 and RAG-02, with API responses stubbed by route interception and the **built frontend** served by a small local static server (no Node dev server, no StrictMode double fetch, no backend needed). UI-10 becomes a build gate in the Maven/CI step, not a browser test. | D-4, D-5; browser agent's inventory |
| 6 | **Known-defect static checks** (`SEC-08`, `SEC-11…16`) tagged separately, run on demand, reported with real failures, excluded from the stable totals | D-7 |
| 7 | **Not implemented, because unsupported:** logout/session, protected pages, all payment scenarios, login UI, `ENR-06/07`, `CAT-09`, DB-dependent search | Inspection and all three agents agree |
| 8 | **No application changes.** No test IDs or ARIA attributes will be added. The proposed fixes (G-01 `aria-pressed`, G-02 `formatCredits`, `@NotBlank` on the enrollment DTO, hashing) need your explicit approval | CLAUDE.md "Current Scope" |
| 9 | **Needs your approval before anything runs:** the Playwright for Java Maven dependency and a Chromium download. I will also rewrite the `playwright-author` skill from Python to Java | CLAUDE.md rule 16 |

## 7. Human-review point

A named human should review: (a) the severity calls in D-2 and D-3; (b) whether characterization tests of insecure behavior (D-6) are acceptable in the suite; (c) the Playwright for Java dependency and Chromium download; (d) whether to rerun `test-analyst` to recover its missing findings. Until then this consolidation is a draft, and the agents' findings are unapproved analysis.
