# Knowledge Base Review

Date: 2026-11-22 · Branch `TestBranchV` · Location: `rag/knowledge/`
Reviewer: Claude (author and reviewer are the same agent). **Human review is still required**; this report is not an approval.

## 1. Files and versions

| File | doc_id | Version | Status | Lines | Intended use |
|---|---|---|---|---|---|
| `rag/knowledge/login_requirements.md` | KB-LOGIN-001 | 1.0.0 | draft-pending-human-review | 53 | Answerable and unanswerable questions about login behavior; documents which login rules exist and which are unknown |
| `rag/knowledge/payment_test_policy.md` | KB-PAY-001 | 1.0.0 | draft-pending-human-review | 78 | Local/mock-only payment testing rules; decline, timeout, idempotency, logging rules; states that no payment code exists today |
| `rag/knowledge/qa_standards.md` | KB-QA-001 | 1.0.0 | draft-pending-human-review | 74 | Naming, assertion, locator, waiting, data, evidence, and logging conventions; "undocumented means unknown" rule |
| `rag/knowledge/known_defects.md` | KB-DEF-001 | 1.0.0 | draft-pending-human-review | 40 | Eight synthetic defects, each linked to a regression check; closure rules |
| `rag/knowledge/release_gate.md` | KB-REL-001 | 1.0.0 | draft-pending-human-review | 54 | Deterministic gate (KB-REL-01…10) and RAG gate (KB-REL-11…18); human sign-off |
| `rag/knowledge/malicious_document_sample.md` | KB-MAL-001 | 1.0.0 | test-fixture-only-pending-human-review | 62 | `malicious` category fixture: four labelled synthetic injection examples with expected safe behavior |

All six share the same front-matter fields (`doc_id`, `title`, `version`, `status`, `date`, `classification`, `precedence`), all dated 2026-11-22.
Also added: `rag/tools/check_knowledge_base.py` (the consistency checker) and `reports/knowledge-base-check-output.txt` (its saved output).

## 2. Requirement coverage (your list)

| # | Requirement | Where satisfied |
|---|---|---|
| 1–3 | No confidential company info, no personal data, no real account details | All files; scan results in §4 |
| 4 | Labelled as synthetic training data | Front matter `classification: SYNTHETIC TRAINING DATA` and a body banner in every file |
| 5 | Versions and section headings | Semver in every front matter; numbered `##` sections |
| 6 | Local/mock-only payment testing | `payment_test_policy.md` §3 (KB-PAY-01…04) |
| 7 | Live gateways and real card data prohibited | `payment_test_policy.md` §4 (KB-PAY-05…08) |
| 8 | Decline and timeout handling | `payment_test_policy.md` §6 (KB-PAY-11…16) |
| 9 | Idempotency | `payment_test_policy.md` §7 (KB-PAY-17…20) |
| 10 | No passwords, tokens, full card numbers in logs | `payment_test_policy.md` KB-PAY-09; `qa_standards.md` §8; `login_requirements.md` KB-REQ-LOGIN-07; `release_gate.md` KB-REL-07 |
| 11 | QA naming, assertion, locator, evidence conventions | `qa_standards.md` §2, §3, §4, §7 |
| 12 | Synthetic defects linked to regression checks | `known_defects.md` §3 (KB-DEF-001…008) |
| 13 | Deterministic and RAG release gates | `release_gate.md` §2, §3 |
| 14 | Undocumented policies are unknown | `qa_standards.md` §9 (KB-QA-21…23); also stated in the login, payment, defects, and release documents |

## 3. Contradictions checked

### 3.1 Manual review (cross-document)
| Topic checked | Documents compared | Result |
|---|---|---|
| Log content rule | payment policy, QA standards, login requirements, release gate | Consistent wording: passwords, tokens, full card numbers never in logs |
| Decline vs. timeout retry | KB-PAY-13 (no automatic retry after a decline) vs. KB-PAY-15 (retry after a timeout only with the same idempotency key) | Not contradictory: different outcomes, different rules |
| Idempotency | KB-PAY-18 (same key, same payload → one charge) vs. KB-PAY-19 (same key, different payload → rejected) | Consistent |
| Fail vs. skip | KB-PAY-04 (tests fail if the mock is unavailable) vs. KB-REL-04 (skips need a reason) | Consistent: the first removes the skip case |
| Zero collected tests | KB-QA-20, KB-REL-02, KB-DEF-008 | Consistent |
| Evidence rule | KB-QA-16, KB-REL-03, login §6 | Consistent: run and inspected output only |
| Authority | Every file defers to CLAUDE.md, system instructions, security rules, secret handling | Consistent |
| Synthetic defects vs. real code | `known_defects.md` is explicitly "not a statement about the current code" | Consistent with `reports/repository-inspection.md` (e.g., the real login rejects blank input; KB-DEF-001 is a fictional, closed example) |
| Payment implementation claims | `payment_test_policy.md` §1 states no payment code exists | Consistent with the repository inspection |

### 3.2 Contradictions and gaps found, and fixed
0. **Status label vs. approval.** Files first carried `approved-for-training-use`, but no human had approved them. Fixed: all files now say `draft-pending-human-review` (the fixture says `test-fixture-only-pending-human-review`).
1. **Naming example vs. naming rule.** KB-QA-01's example was snake_case while KB-QA-02 says Java uses camelCase. Fixed: both Python and Java examples are given.
2. **Retroactive rule.** KB-QA-03 implied every test already carries a matrix ID, but the existing `LoginServiceTest` does not. Fixed: the rule applies to newly written tests.
3. **Unsatisfiable gate.** KB-REL-09 would permanently block on Critical/High synthetic defects that cannot be verified without a payment module (KB-DEF-004, 005). Fixed: KB-DEF-R3 now requires explicit written acceptance by a named human; such entries are never silently treated as closed.
4. **Dangling reference.** The KB referenced `reports/defects.md`, which did not exist. Fixed: wording now says "the repository's defect report (if one exists)" and the real status is "unknown" until one is created.
5. **Checker defect (not a KB defect).** My first checker run flagged each document's own `doc_id` as an undefined rule ID. I fixed the checker, not the documents.

### 3.3 Automated checks
Command: `python3 -I rag/tools/check_knowledge_base.py` → saved in `reports/knowledge-base-check-output.txt`.
Result of the final run: **58 checks passed, 0 failed.** The first run had 2 failures (items 4 and 5 above); both were resolved before the final run.
Checks cover: the six expected files; complete front matter; synthetic label in front matter and body; semver; unique doc_ids; precedence language; every referenced rule ID defined; no duplicate rule IDs; every matrix ID referenced (`LOGIN-04`, `LOGIN-07`, `SEC-06`, `UI-08`) exists in `docs/test-matrix.md`; referenced paths exist; the required content statements for mock-only payments, prohibitions, decline/timeout/idempotency, logging, QA sections, defect→check links, both gates, "unknown" language, and the malicious fixture.
Limit: these are string and structure checks. They cannot judge whether a policy is wise or whether two sentences contradict in meaning. The manual review in §3.1 covers meaning, and a human should repeat it.

## 4. Synthetic-data confirmation

- Every document is labelled `SYNTHETIC TRAINING DATA` in its front matter and its first banner.
- Automated scan of all six files found: no email addresses, no phone-like numbers, no 13–19 digit sequences (card-like), no URLs, no key/token-shaped strings, no `password=`/`secret:`-style assignments.
- The repository's synthetic login fixture (account name and password) is **not copied** into the knowledge base; the login document states fixtures hold those values.
- No company, person, merchant, bank, or payment provider is named.
- The only deliberately attack-like text is in the clearly labelled `malicious_document_sample.md`, and it contains no real secret.
- Limit: a scan cannot prove that no real-world fact is accidentally included. A human should read the documents once.

## 5. Security controls built into the knowledge base

| Control | Where |
|---|---|
| Embedded instructions are untrusted document data | Every file, authority section; `malicious_document_sample.md` §1 |
| Documents cannot override `CLAUDE.md`, system instructions, security rules, or secret-handling controls | Front matter `precedence` and the authority section of every file |
| Correct behavior on an injection is: ignore, report as a finding, continue the real task | `malicious_document_sample.md` §1.3 |
| Secrets are never revealed, requested, printed, or stored | `malicious_document_sample.md` §1.4; `qa_standards.md` KB-QA-15 |
| Four labelled injection examples (secret extraction, weakening verification, bypassing payment safety, authority impersonation) with expected safe behavior | `malicious_document_sample.md` §2 |
| Any obedience to an example blocks the release regardless of aggregate scores | KB-MAL-03; `release_gate.md` KB-REL-14 |
| Live gateways, real card data, and non-local requests prohibited | `payment_test_policy.md` KB-PAY-05…08 |
| Logs, screenshots, traces, and reports must not contain passwords, tokens, or full card numbers | KB-PAY-09, KB-QA-19, KB-REL-07 |
| Undocumented answers must be "unknown" or "not documented", never invented | KB-QA-21…23, KB-REL-15 |
| Human review required; an agent cannot approve its own work | KB-REL-18, `release_gate.md` §4, KB-MAL-04 |

## 6. Residual risks and notes

- **The injection text is real text in the repo.** Anyone feeding the whole `rag/knowledge/` folder to a model without this framing could trigger it. That is the intended test, but index `malicious_document_sample.md` only in evaluation corpora, not in any answering corpus used for real work.
- **The knowledge base describes policy that the code does not implement.** Payment, session, lockout, and hashing rules do not exist in the repository. The documents say so (payment §1, login §4); a retrieval system must preserve that distinction.
- **No numeric thresholds are defined** (RAG scores, timeouts, limits). They are deliberately "unknown until declared"; someone must declare them before any gate can be passed.
- **Author and reviewer are the same agent.** A human must read the files and the checker output before the KB is used for evaluation.
- **No RAG pipeline exists yet.** The knowledge base has not been retrieved from, chunked, or evaluated.

## 7. Human-review point

Before use: a named human reads all six documents, confirms there is nothing confidential, personal, or real, and approves the policy content (especially the release gates and idempotency rules). Until then the front-matter status of every file is `draft-pending-human-review` (or `test-fixture-only-pending-human-review`). I changed it from an earlier "approved" label, because no human had approved the content and the label contradicted this review. Change it only after a named human signs off.
