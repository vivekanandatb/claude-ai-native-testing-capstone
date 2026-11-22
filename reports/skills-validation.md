# Skills Validation

Date: 2026-11-20 · Branch `TestBranchV` · Claude Code **2.1.126**
Skills location: `.claude/skills/` (repo root). No application files were modified.

## 1. Files created

| Skill | Path | Lines |
|---|---|---|
| test-plan | `.claude/skills/test-plan/SKILL.md` | 94 |
| playwright-author | `.claude/skills/playwright-author/SKILL.md` | 69 |
| rag-evaluation | `.claude/skills/rag-evaluation/SKILL.md` | 99 |

Frontmatter used (all accepted by Claude Code 2.1.126; no parse warnings on stderr): `name`, `description`, `argument-hint`, `allowed-tools`. `allowed-tools` limits each skill to read/search tools and writes under a specific folder (`docs/`, `reports/`, `tests/`, or `evals/`), plus narrowly scoped Bash commands.

## 2. Detection test: PASS

Command: `claude -p "Reply with the single word OK." --output-format stream-json --verbose`, then read the `system/init` event (saved to `reports/skills-validation-evidence/detection-init.json`).

Result: `skills` contained `test-plan`, `playwright-author`, and `rag-evaluation`, and all three also appeared in `slash_commands`. Version reported: 2.1.126. The headless session ran on model `claude-sonnet-4-6` (a different model from the interactive session).

## 3. Functional test of `test-plan` with "A user can make payment."

Command (run twice): `claude -p '/test-plan A user can make payment.' --output-format text`. Outputs saved:
- `reports/skills-validation-evidence/test-plan-run1-before-tightening.md`
- `reports/skills-validation-evidence/test-plan-run2-after-tightening.md`

Both runs used read-only inspection; "Files written: none" in each report. `git status` after each run showed no changes beyond the pre-existing untracked directories.

### Run 1 (original skill): identified gaps, with four defects

Passed the key check. The skill:
- restated the requirement verbatim;
- listed the facts it found (no payment controller, endpoint, UI, gateway dependency; payment already marked unsupported in `docs/`);
- listed open questions for payment method/entry point, fields, **currency and amount range (Q5)**, success and failure outcomes, **gateway/provider (Q8)**, idempotency (Q9), **timeout (Q10)**, **refund/reversal (Q11)**, logging, accessibility, performance and CSRF;
- marked every dependent expected result "Undetermined, blocked by Qn";
- named no currency, limit, provider, refund policy or timeout value.

Defects found by reading the output:
1. **Domain assumptions leaked in.** It assumed a card payment (Luhn check, expired card, "gateway test card") although the payment method was an open question.
2. **Open questions were dropped.** Q11 (refunds), Q12 (logging) and Q14 (performance) appeared in the unknowns list but not in the final open-questions table, and no refund scenario row existed.
3. **A citation was wrong.** It cited `LoginService.java:99–102` for an injection pattern, but that file has about 48 lines and the pattern is in the test file.
4. **A concrete expected result was not backed by the requirement** (for example PAY-07, PAY-17 stated specific outcomes for fields and card data the requirement never defines).

### Skill change (three small edits to `test-plan/SKILL.md`)
- Forbids importing unstated domain assumptions (payment method, validation algorithm, test-card behavior) and requires method-neutral wording.
- Requires a placeholder `Blocked(Qn)` scenario row for each undefined gap category (refunds, timeouts, logging, performance).
- Requires evidence citations only from files actually read, line numbers verified, and the final open-questions list to contain every unknown from Step 2 with the same IDs.

### Run 2 (tightened skill): PASS on the requested check

| Check | Result (from the saved output) |
|---|---|
| Identifies missing information instead of inventing it | Yes: 16 unknowns (Q1–Q16) |
| No invented currency | Pass. Q7 asks which currency; none named |
| No invented limits | Pass. Q7 asks for amount ranges; no numbers |
| No invented provider | Pass. Q4 asks which provider; see note below |
| No invented refund rules | Pass. Q12 asks whether refunds are in scope; placeholder row `PAY-12` is `Blocked(Q12)` |
| No invented timeout behavior | Pass. Q10 asks what happens on timeout |
| Assumptions list | "None": nothing was allowed to drive an expected result |
| Unknowns carried to the final list | Pass. Q1–Q16 appear in both lists (checked with a script) |
| Card-specific assumptions (Luhn, test cards, expired card) | Absent (grep found none) |
| Unsupported scenarios | All payment scenarios marked Unsupported or `Blocked`; one executable regression scenario (run existing login tests) |
| Files modified | None |

Residual note: Q2 and Q4 list example options inside the questions ("credit card, debit, bank transfer…", "Stripe, PayPal, Square, Braintree, other"). They are offered as example answers, not as claims about the system, so I count them as acceptable, but a stricter reader may prefer none.

Limit of this test: it is one requirement on one run per skill version. LLM output varies between runs, so this is evidence of behavior, not a guarantee.

## 4. Not validated (honest scope)

- `playwright-author` and `rag-evaluation` were **detected but not exercised**. Only `test-plan` was run.
- `pip show` finds **none** of `pytest`, `playwright`, `pytest-playwright`, `ragas`; no browser check was made. Using those skills for real needs approved installs, and `rag-evaluation` needs a judge model (an API key in an environment variable, and consent to send data to that provider).
- The repository has **no RAG component**, so `rag-evaluation` should stop at Step 0 today and only offer a dataset design. This behavior is specified in the skill but was not tested.
- The commands in each skill's `allowed-tools` were not checked against every command the skills tell Claude to run. If a needed command is outside the list, Claude Code will ask for permission.

## 5. Evidence files

- `reports/skills-validation-evidence/detection-init.json`
- `reports/skills-validation-evidence/test-plan-run1-before-tightening.md`
- `reports/skills-validation-evidence/test-plan-run2-after-tightening.md`
