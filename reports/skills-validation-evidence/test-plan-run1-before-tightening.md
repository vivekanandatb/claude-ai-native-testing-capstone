Now I have a complete picture of the repository. Let me compose the test plan.

---

# Test Plan: A user can make payment.

## Requirement (verbatim)

> A user can make payment.

---

## Step 1: Evidence Summary

Key files read during inspection:

| File | Key Finding |
|---|---|
| `payment-login-java-project/src/main/java/com/technova/payment/Main.java` | Prints "Login functionality is intentionally not implemented." No payment code. |
| `payment-login-java-project/src/main/java/com/technova/payment/auth/LoginService.java` | Authentication only; no PaymentService |
| `payment-login-java-project/src/main/java/com/technova/payment/auth/UserRepository.java` | In-memory user store; no payment/transaction models |
| `payment-login-java-project/pom.xml` | Only dependency: `junit-jupiter:5.10.2`; no gateway SDK |
| `starter_code/backend/src/main/java/com/globallearning/lms/catalog/web/` | CourseController, EnrollmentController, LearnerController — no payment endpoints |
| `starter_code/backend/src/main/resources/application.yml` | LMS catalog DB config; no payment gateway config |
| `starter_code/frontend/src/` | CourseList, CourseCard, App.jsx — no payment UI |
| `docs/test-strategy.md:46,56–59` | Q1: "Is a payment module expected? None exists in either code base." |
| `docs/requirements-traceability.md:22–23` | Payment marked UNSUP-PAY-01…06, **Unsupported** |
| `reports/repository-inspection.md:93–98,151` | "No payment code, route, gateway reference" — confirmed |
| `payment-login-java-project/target/surefire-reports/TEST-com.technova.payment.auth.LoginServiceTest.xml` | 24 login tests, 0 failures, last run |

---

## Step 2: Four Lists

### Confirmed Facts

| Fact | File / element | Confidence |
|---|---|---|
| No payment controller, service, DTO, or model exists in `starter_code/` or `payment-login-java-project/` | `starter_code/backend/src/main/java/…/web/`, `auth/` package | confirmed |
| No payment endpoint (e.g. `POST /payment`) exists | Grep across `starter_code/backend` | confirmed |
| No payment UI component, page, or form exists in React frontend | `starter_code/frontend/src/` | confirmed |
| No payment gateway dependency in `pom.xml` or `package.json` | `payment-login-java-project/pom.xml`, `starter_code/backend/pom.xml` absent | confirmed |
| `LoginService.login()` exists and has 24 passing unit tests | `auth/LoginService.java:32–45`, surefire XML | confirmed |
| `CLAUDE.md` prohibits live gateways and real cardholder data | `CLAUDE.md:10–11` | confirmed |
| Prior planning docs already flagged payment as unsupported | `docs/requirements-traceability.md:22`, `docs/test-strategy.md:56–59` | confirmed |

### Assumptions

| Label | Assumption | Why it cannot drive an expected result |
|---|---|---|
| A1 | Payment is intended to be built into the `payment-login-java-project` package, not `starter_code` | No code exists; placement is a design decision |
| A2 | A user must be logged in before making a payment | No auth guard implemented for payment; unverifiable |
| A3 | Payment is linked to course enrollment (pay to enroll) | No enrollment–payment relationship in any model |

### Unknowns / Open Questions

| ID | Missing detail |
|---|---|
| Q1 | Is a payment module planned? No implementation exists in either sub-project. |
| Q2 | What is the entry point for payment? (After login? From course page? Standalone URL?) |
| Q3 | What payment methods are supported? (Card, bank transfer, wallet, voucher?) |
| Q4 | What fields does the payment form have, and what are their names and types? |
| Q5 | What currency and what valid amount range are accepted? (Min, max, decimal precision?) |
| Q6 | What is the observable success outcome? (HTTP status, redirect URL, confirmation message text?) |
| Q7 | What is the observable decline/failure outcome? (Error message text, status code, retry allowed?) |
| Q8 | What payment gateway or provider is used? Is a sandbox/test mode available? |
| Q9 | What are the idempotency rules? Can a user submit twice? Is there a unique transaction key? |
| Q10 | What are the timeout rules for a payment request? |
| Q11 | Is refund, reversal, or cancellation in scope for this requirement? |
| Q12 | What data is logged at payment events? Is PAN/CVV ever written to logs? |
| Q13 | What accessibility requirements apply to the payment form? (WCAG level, SR announcements?) |
| Q14 | What performance SLA applies to the payment endpoint? (p95 latency, throughput?) |
| Q15 | Is CSRF protection required on the payment endpoint? |

### Unsupported Scenarios

All payment-specific scenarios are unsupported: no controller, service, DTO, model, UI, or gateway integration exists. The only testable existing code is `LoginService`, which is covered by existing tests.

---

## Step 3 & 4: Test Scenario Table

> **Key:** All PAY-01 through PAY-22 have Status **Unsupported** because no payment code exists. Expected results depending on unknowns are marked "Undetermined, blocked by Q-n". PAY-23 covers regression against the existing login implementation and is `Not started` (implementable today).

| ID | Requirement / observed behavior | Risk | Priority | Preconditions | Steps | Expected result (observable) | Automation layer | Evidence | Det / AI | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| **PAY-01** | Authenticated user submits valid payment details and payment succeeds | Revenue not collected; user blocked from purchased content | P0 | User logged in (Q2); payment module deployed (Q1); gateway sandbox active (Q8) | 1. Log in with valid credentials. 2. Navigate to payment entry point. 3. Enter synthetic valid card details. 4. Submit form. | Undetermined, blocked by Q6, Q8 | Browser (UI form + network response) | None — see Q1, Q6, Q8 | Deterministic | Unsupported |
| **PAY-02** | Success screen / confirmation shown after approved payment | User has no receipt; support burden increases | P0 | PAY-01 completes | Observe UI state after PAY-01. | Undetermined, blocked by Q6 | Browser | None — see Q6 | Deterministic | Unsupported |
| **PAY-03** | Invalid card number is rejected before gateway call | Unnecessary gateway charge / latency | P1 | Payment form rendered (Q4) | Enter a card number failing Luhn check. Submit. | Undetermined, blocked by Q4, Q7 | Unit / Browser | None — see Q4 | Deterministic | Unsupported |
| **PAY-04** | Expired card is rejected | Customer frustration; failed transaction | P1 | Payment form rendered | Enter card with past expiry date. Submit. | Undetermined, blocked by Q7 | Unit / Browser | None — see Q7 | Deterministic | Unsupported |
| **PAY-05** | Gateway-declined payment shows user-facing error | Silent failure; user retries without understanding | P1 | Gateway sandbox returns decline code (Q8) | Use gateway test card for decline. Submit. | Undetermined, blocked by Q7, Q8 | Browser (mocked gateway) | None — see Q7, Q8 | Deterministic | Unsupported |
| **PAY-06** | Missing required field triggers inline validation | Form submits incomplete data to gateway | P1 | Payment form rendered | Leave one required field empty. Attempt submit. | Undetermined, blocked by Q4, Q7 | Browser | None — see Q4 | Deterministic | Unsupported |
| **PAY-07** | Injection-style string in payment text field is treated as literal | XSS / SQLi stored and replayed in receipts | P1 | Payment form rendered | Enter `<script>alert(1)</script>` in name or address field. Submit. | String stored and displayed as literal; no script execution | Unit / Browser | `LoginService.java:99–102` (analogous SQL injection test pattern) | Deterministic | Unsupported |
| **PAY-08** | Amount at documented minimum is accepted | Edge-of-range rejection blocks legitimate payments | P2 | Min amount defined (Q5) | Submit payment at exact minimum. | Undetermined, blocked by Q5, Q6 | Unit | None — see Q5 | Deterministic | Unsupported |
| **PAY-09** | Amount one unit below minimum is rejected | | P2 | Min amount defined (Q5) | Submit payment one unit below minimum. | Undetermined, blocked by Q5, Q7 | Unit | None — see Q5 | Deterministic | Unsupported |
| **PAY-10** | Amount at documented maximum is accepted | | P2 | Max amount defined (Q5) | Submit payment at exact maximum. | Undetermined, blocked by Q5, Q6 | Unit | None — see Q5 | Deterministic | Unsupported |
| **PAY-11** | Amount one unit above maximum is rejected | Overcharging or gateway error | P2 | Max amount defined (Q5) | Submit payment one unit above maximum. | Undetermined, blocked by Q5, Q7 | Unit | None — see Q5 | Deterministic | Unsupported |
| **PAY-12** | Unauthenticated request to payment endpoint returns 401/redirect | Unauthorized payment on behalf of another user | P0 | Payment endpoint defined (Q1, Q2) | Send payment request without session/token. | HTTP 401 or redirect to login — undetermined, blocked by Q2 | API | None — see Q1 | Deterministic | Unsupported |
| **PAY-13** | Cross-user payment not possible (access control) | Billing another user's account | P0 | Two user accounts; payment endpoint defined | Submit payment using user A's session for user B's order. | Undetermined, blocked by Q1, Q2 | API | None — see Q1 | Deterministic | Unsupported |
| **PAY-14** | CSRF token required on payment form submission | CSRF-triggered unintended charge | P1 | CSRF protection status defined (Q15) | Submit payment form without CSRF token. | Undetermined, blocked by Q15 | API | None — see Q15 | Deterministic | Unsupported |
| **PAY-15** | Double-submitting the payment form does not charge twice (idempotency) | Double charge; reconciliation error | P0 | Idempotency key defined (Q9) | Submit valid payment; immediately submit again. | Undetermined, blocked by Q9 | API / Browser | None — see Q9 | Deterministic | Unsupported |
| **PAY-16** | Payment request that times out shows user-facing error (not silent hang) | User retries unknowing charge already posted | P1 | Timeout value defined (Q10); gateway mock configurable for delay | Configure mock gateway to delay beyond timeout. Submit payment. | Undetermined, blocked by Q10, Q7 | API (mock gateway) | None — see Q10 | Deterministic | Unsupported |
| **PAY-17** | Card number and CVV do not appear in application logs or HTTP responses | PCI DSS violation; cardholder data exposure | P0 | Payment request submitted; log output accessible | Submit payment; inspect server logs, response body, and browser network tab. | No PAN, CVV, or full card number present in any log line or response payload | Static / API | `CLAUDE.md:11` (no cardholder data) | Deterministic | Unsupported |
| **PAY-18** | Payment form is fully keyboard-navigable | Keyboard users cannot complete purchase | P1 | Payment form rendered; accessibility requirements defined (Q13) | Tab through all form fields; submit with Enter/Space. | All fields reachable by Tab; submit reachable; no keyboard trap | Browser | None — see Q13 | Deterministic | Unsupported |
| **PAY-19** | Inline validation errors are announced to screen readers | Blind users cannot correct payment errors | P1 | Accessibility requirements defined (Q13) | Trigger a field validation error; observe ARIA live region or `aria-describedby`. | Error message text exposed via accessible name or live region | Browser | None — see Q13 | Deterministic | Unsupported |
| **PAY-20** | Payment form fields have accessible labels | Screen readers cannot identify field purpose | P1 | Accessibility requirements defined (Q13) | Inspect rendered DOM for `<label for>` or `aria-label` on each input. | Each input has a visible label or `aria-label` matching field purpose | Static / Browser | None — see Q13 | Deterministic | Unsupported |
| **PAY-21** | Very long string in payment text field does not crash or truncate silently | Buffer overflow / data loss | P2 | Field length limits defined (Q4) | Enter 10 000-character string in name or address field. | Undetermined, blocked by Q4, Q7 | Unit / Browser | None — see Q4 | Deterministic | Unsupported |
| **PAY-22** | Unicode characters in cardholder name are handled correctly | Garbled name on receipt; mismatch with bank | P2 | Field encoding behavior defined | Enter name with emoji, Arabic, or CJK characters. Submit. | Undetermined, blocked by Q4 | Unit | None — see Q4 | Deterministic | Unsupported |
| **PAY-23** | Adding payment module does not break existing login behavior | Regression — login stops working for all users | P0 | `LoginService` tests currently passing (24/24, surefire XML) | Run `mvn test` in `payment-login-java-project/`; verify all 24 `LoginServiceTest` assertions still pass after any payment code is added. | 24 tests pass, 0 failures, 0 errors | Unit (`LoginServiceTest`) | `target/surefire-reports/TEST-com.technova.payment.auth.LoginServiceTest.xml` | Deterministic | Not started |

---

## Traceability

**Requirement: "A user can make payment."**

| Behavior | Scenario IDs |
|---|---|
| Happy-path payment success | PAY-01, PAY-02 |
| Input validation (client-side) | PAY-03, PAY-04, PAY-06, PAY-21, PAY-22 |
| Gateway decline handling | PAY-05 |
| Security — injection | PAY-07 |
| Boundary — amount | PAY-08, PAY-09, PAY-10, PAY-11 |
| Authorization / access control | PAY-12, PAY-13, PAY-14 |
| Idempotency / double-submit | PAY-15 |
| Timeout handling | PAY-16 |
| PCI / data leakage | PAY-17 |
| Accessibility | PAY-18, PAY-19, PAY-20 |
| Regression — login | PAY-23 |

---

## Highest-Risk Five

| Rank | ID | Reason |
|---|---|---|
| 1 | **PAY-15** (idempotency) | Double charges cause direct financial harm and are very hard to reverse; this risk is entirely unmitigated with no payment code in place. |
| 2 | **PAY-17** (PAN/CVV in logs) | A single log line containing card data is a PCI DSS incident. No logging policy exists yet; the risk is introduced from day one of implementation. |
| 3 | **PAY-12** (unauthenticated access) | The existing app has no session guard on any endpoint; a payment endpoint added without an auth check would be immediately exploitable. |
| 4 | **PAY-01** (happy path) | No happy path means zero revenue and zero ability to validate any other scenario. Everything else depends on this. |
| 5 | **PAY-23** (login regression) | The only currently passing test suite is for login; payment code added to the same package could inadvertently break the only working component. |

---

## Recommended Automation Order

1. **PAY-23** — Run `mvn test` after any new code is added; zero cost, protects the only green test suite. *(Runnable today.)*
2. **PAY-12, PAY-13, PAY-14** — Auth and CSRF guards are cheapest to verify at the API layer with mocked sessions; implement before any UI.
3. **PAY-17** — Log-scanning test (static or integration); can be automated with a simple regex check on log output.
4. **PAY-15** — Idempotency test at the service layer; requires the payment service to exist but no UI.
5. **PAY-01, PAY-02** — Happy-path browser test; unblock with gateway sandbox (Q8) and UI (Q4).
6. **PAY-03–PAY-11** — Field validation and boundary tests; implement in unit tests once the service layer exists.
7. **PAY-18–PAY-20** — Accessibility tests; implement as browser tests once the UI form exists (Q4, Q13).

---

## Open Questions for the User

| ID | Question | Blocks |
|---|---|---|
| **Q1** | Is a payment module planned? If so, in which sub-project and on what timeline? | All PAY-01–PAY-22 |
| **Q2** | What is the entry point for payment? URL, trigger event, or screen? | PAY-01, PAY-12, PAY-13 |
| **Q3** | What payment methods are in scope? | PAY-01–PAY-11 |
| **Q4** | What fields does the payment form have (name, type, max length)? | PAY-03–PAY-07, PAY-18–PAY-22 |
| **Q5** | What currency, minimum amount, and maximum amount apply? | PAY-08–PAY-11 |
| **Q6** | What is the observable success state? (Message text, redirect, HTTP status?) | PAY-01, PAY-02, PAY-08, PAY-10 |
| **Q7** | What is the observable error/decline state? (Message text, status code?) | PAY-03–PAY-06, PAY-09, PAY-11, PAY-16 |
| **Q8** | Which gateway or provider is used, and is a sandbox mode available? | PAY-01, PAY-05, PAY-16 |
| **Q9** | How is idempotency enforced? (Unique key? Session lock? Server-side dedup?) | PAY-15 |
| **Q10** | What is the payment request timeout, and what happens on expiry? | PAY-16 |
| **Q15** | Is CSRF protection required? Which mechanism? | PAY-14 |
| **Q13** | What WCAG conformance level is required for the payment form? | PAY-18–PAY-20 |

---

## End-of-Plan Report

**Files read:** 15 source/config/doc files across `payment-login-java-project/`, `starter_code/`, `docs/`, `reports/`

**Files written:** none

**Commands run:** none (read-only inspection via Explore agent)

**Unresolved risks:**
- Payment module does not exist; 22 of 23 scenarios are unsupported.
- No gateway SDK, no payment endpoint, no UI form, no payment model.
- All expected results for payment scenarios are undetermined pending answers to Q1–Q15.
- PAY-17 (PAN in logs) and PAY-15 (idempotency) are highest-harm risks with no mitigation path until Q1 is answered.

**Recommended next step:** Answer **Q1** (is a payment module planned, and where?). Until that is confirmed, the only actionable task is PAY-23 — run `mvn test` in `payment-login-java-project/` to establish a green baseline before any new code is introduced.
