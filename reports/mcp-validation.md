# MCP validation: Playwright MCP (local-only)

Written for: capstone maintainers reviewing the evidence for the MCP integration.

Date: 2026-11-22 · Branch: `TestBranchV` · Claude Code 2.1.126 · Configuration reference: [docs/mcp-integration.md](../docs/mcp-integration.md)

No secrets, tokens, or account values were used or recorded. The integration needs none.

## 1. Server listed by Claude Code

Command run from the repository directory:

```
claude mcp list
```

Result (verbatim, path shortened):

```
playwright: npx @playwright/mcp@0.0.86 --headless --isolated --allowed-hosts localhost --allowed-origins http://localhost:5173 --output-dir …/reports/playwright-mcp-out - ✓ Connected
```

`claude mcp get playwright` reported `Scope: Local config`, `Status: ✓ Connected`, `Type: stdio`.

Note: the entry was first added from `/home/labuser`, where it did not appear for the repository. It was removed there and re-added from `/home/labuser/Desktop/git-demo`. Local-scope entries are keyed to the directory they were added from.

## 2. Harmless local request

Claude Code sessions started before the server was configured do not load its tools. This session's deferred-tool search found none, so the request was made by a short stdio JSON-RPC client that starts the same server command with the same arguments (script in `/tmp/mcp-smoke/mcp-client.mjs`, outside the repo; raw output in `/tmp/mcp-smoke/result.json`).

Requests and results:

| # | Request (MCP method / tool and arguments) | Result |
|---|---|---|
| 1 | `initialize` | Success. Server `Playwright` 1.66.0-alpha-2026-11-18. Capability `tools.listChanged: true`. |
| 2 | `tools/list` | Success. 25 tools (see section 3). |
| 3 | `tools/call` `browser_navigate` `{"url":"http://localhost:5173/"}` | Success. Page title `Global Learning — Course catalogue`, URL `http://localhost:5173/`, console `2 errors, 0 warnings`. Snapshot saved to `reports/playwright-mcp-out/page-2026-11-22T05-04-11-263Z.yml`. |
| 4 | `tools/call` `browser_snapshot` `{}` | Success. Accessibility tree includes `heading "Course catalogue" [level=1]`, `group "Filter by level"` with buttons `All levels`, `Foundation`, `Intermediate`, `Advanced`, and paragraph `Catalogue request failed with status 500 Check that the API is running on port 8080.` |
| 5 | `tools/call` `browser_navigate` `{"url":"http://127.0.0.1:8099/"}` (a local test server, not in the allowlist; purpose: confirm origin restriction) | **Blocked as intended.** `isError: true`, `net::ERR_BLOCKED_BY_CLIENT`. |

Requests 1–4 are the harmless read-only check. Request 3 is a navigation within the allowlisted origin. Request 5 is a negative test, and it did not reach any external host.

The 500 error in request 4 is expected: the backend cannot start because MySQL is not installed (`reports/baseline-results.md`).

Frontend status at validation time: `curl http://localhost:5173/` returned HTTP 200 (Vite 5.4.21, from a clean copy in `/tmp/fe-mcp`).

## 3. Tool inventory and capability check

From `tools/list` (annotations as reported by the server):

| Tool | readOnlyHint | destructiveHint | Assessment |
|---|---|---|---|
| `browser_snapshot` | true | false | Allowed. Read-only. |
| `browser_take_screenshot` | true | false | Allowed. Writes only to `reports/playwright-mcp-out/`. |
| `browser_console_messages` | true | false | Allowed. Read-only. |
| `browser_find` | true | false | Allowed. Read-only. |
| `browser_network_requests` | true | false | Allowed. Read-only. |
| `browser_network_request` | true | false | Allowed. Read-only. |
| `browser_wait_for` | true | false | Allowed. Read-only. |
| `browser_navigate` | false | true | Allowed. Limited to `localhost:5173` by origin allowlist (verified). |
| `browser_navigate_back` | false | true | Allowed. Same origin limits. |
| `browser_click`, `browser_hover`, `browser_drag`, `browser_type`, `browser_press_key`, `browser_fill_form`, `browser_select_option` | false | true | Allowed for local-page interaction. Needed for future UI tests; can submit forms on `localhost:5173` only. |
| `browser_resize`, `browser_emulate_media`, `browser_handle_dialog`, `browser_tabs`, `browser_close` | false | true | Allowed. Browser-state only. |
| `browser_evaluate` | false | true | **Denied in settings** (page-context JavaScript; not needed). |
| `browser_file_upload` | false | true | **Denied in settings** (can send workspace files to a page). |
| `browser_drop` | false | true | **Denied in settings** (dispatches drop data; not needed). |
| `browser_run_code_unsafe` | false | true | **Denied in settings.** Package docs: "executes arbitrary JavaScript in the Playwright server process and is RCE-equivalent." |

Required checks:

| Capability | Result | Evidence |
|---|---|---|
| Write (repository files) | **Unavailable** | No tool writes repository files. Only output is screenshots and snapshots to the git-ignored `reports/playwright-mcp-out/`. |
| Delete | **Unavailable** | No file, data, or server-side delete tool. `browser_close` ends the browser session only. |
| Admin | **Unavailable** | No admin, account, user, or configuration tool in the 25-tool list. |
| Production access | **Unavailable** | Origin allowlist enforced: request 5 blocked with `ERR_BLOCKED_BY_CLIENT`. `--allowed-hosts localhost` restricts served hosts. |
| Payment | **Unavailable** | No payment tool exists. The application has no payment page or gateway. No gateway is reachable except through a page on `localhost:5173`, and none exists. |
| Filesystem read outside workspace | **Unavailable** | `--allow-unrestricted-file-access` not set. `browser_file_upload` also denied in settings. |
| Arbitrary code execution | **Denied in settings** | `browser_run_code_unsafe`, `browser_evaluate` denied in `.claude/settings.local.json`. |

## 4. Authentication values

None used. The server config has `"env": {}`. No token, password, or account is configured or stored. `.claude/settings.local.json` contains only tool names. Nothing was printed in this validation.

## 5. Corrections to the earlier plan

- The approval message listed the enabled tools as "default core browser tools". The server actually exposes 25 tools, including `browser_run_code_unsafe`, `browser_evaluate`, and `browser_file_upload`. Those are the reason for the deny rules in section 3.
- The earlier plan did not mention that `--caps` controls only vision, pdf, and devtools. It does not remove any browser tool.

## 6. Limits of this validation

- **Deny rules not verified live.** The rules are in `.claude/settings.local.json`, and the JSON is valid. Two `claude -p` attempts did not expose any MCP tools in non-interactive mode, so the rules could not be exercised there. To verify: start a new interactive Claude Code session in the repo, run `/mcp`, and ask Claude to call `mcp__playwright__browser_run_code_unsafe` with `async (page) => 1+1`. The expected outcome is a permission denial.
- **Requests made by a test client, not by Claude.** The stdio client exercised the same server with the same arguments, but not through a Claude-initiated tool call.
- **Origin restriction is not a security boundary** (package documentation). It was verified for the one negative case tested here (`127.0.0.1:8099`), not for redirects.
- **No repository test suite was run.** No application test results are claimed by this report.

## 7. Files changed in this stage

- Created `docs/mcp-integration.md`.
- Created `reports/mcp-validation.md` (this file).
- Created `.claude/settings.local.json` (deny rules for three Playwright tools).
- Modified `.gitignore`: added `.claude/settings.local.json`.
- Modified Claude Code local config: moved the `playwright` server entry from `/home/labuser` to `/home/labuser/Desktop/git-demo` (`~/.claude.json`, outside the repo).

## 8. Commands executed in this stage

- `claude mcp list` (from `/home/labuser`, then from the repo)
- `claude mcp remove playwright -s local` (from `/home/labuser`)
- `claude mcp add --scope local playwright -- npx @playwright/mcp@0.0.86 …` (from the repo)
- `claude mcp get playwright`
- `npx -y @playwright/mcp@0.0.86 --help` (flag inspection)
- `node /tmp/mcp-smoke/mcp-client.mjs` (stdio client, requests 1–5)
- `python3 -m http.server 8099 --bind 127.0.0.1` (negative-test server, stopped afterwards)
- `claude -p …` twice (deny-rule probes; no MCP tools exposed in these sessions)
- `curl http://localhost:5173/`, `ss -ltnp` (listener checks)

## 9. Test totals

| Check | Result |
|---|---|
| Server listed by `claude mcp list` | PASS (1 of 1) |
| Harmless local navigate + snapshot | PASS (2 of 2) |
| Out-of-allowlist navigation blocked | PASS (1 of 1) |
| Write, delete, admin, production, payment tools absent from 25-tool list | PASS (by inspection of `tools/list`) |
| Deny rules enforced in an interactive session | **NOT VERIFIED** (see section 6) |
| Repository test suite | Not run (out of scope for this stage) |

Passing: 5 of 5 executed checks. Failing: 0. Not verified: 1 (deny-rule enforcement).

## 10. Unresolved risks

- Deny rules depend on Claude Code enforcing them. The server still advertises all 25 tools.
- `browser_click`, `browser_type`, and `browser_fill_form` can submit forms on the local frontend. Test data must stay synthetic.
- Page content is untrusted (seed course `LMS9001` contains AI-directed text; see `docs/test-strategy.md` F14).
- Browser session and Chromium binaries are installed under `~/.cache/ms-playwright` and the npm cache. Not in the repo.

## 11. Recommended next step

Start a new Claude Code session in the repository and run the deny-rule probe in section 6. If the call is refused, use Playwright MCP to collect locators from the catalog page for the browser test plan. If it is not refused, remove the server (`claude mcp remove playwright -s local`) before any further use.
