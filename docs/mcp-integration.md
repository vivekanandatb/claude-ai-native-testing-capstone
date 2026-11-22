# MCP integration: Playwright MCP (local-only)

Written for: capstone maintainers and testers who need to know what the browser MCP server can and cannot do here.

Status: configured and validated. Evidence is in [reports/mcp-validation.md](../reports/mcp-validation.md).

## Summary

| Item | Value |
|---|---|
| Server | `playwright` |
| Package | `@playwright/mcp@0.0.86` (npm; source `github.com/microsoft/playwright-mcp`, Apache-2.0, Microsoft) |
| Runtime | Node 22 via `npx`; Chromium 1255 headless shell installed for Playwright 1.66.0-alpha-2026-11-18 |
| Scope | Local (`~/.claude.json`, project `/home/labuser/Desktop/git-demo`). Not committed. No `.mcp.json`. |
| Target | `http://localhost:5173` only (catalog frontend, Vite dev server) |
| Authentication | None. No token, account, or environment variable is required. |

## Registration command

```
claude mcp add --scope local playwright -- npx @playwright/mcp@0.0.86 \
  --headless --isolated \
  --allowed-hosts localhost \
  --allowed-origins "http://localhost:5173" \
  --output-dir /home/labuser/Desktop/git-demo/reports/playwright-mcp-out
```

Flags:
- `--headless`: no visible browser window.
- `--isolated`: browser profile kept in memory; nothing persisted between runs.
- `--allowed-hosts localhost`: DNS-rebinding protection; only `localhost` is served.
- `--allowed-origins http://localhost:5173`: the browser may request only this origin. This is **not** a security boundary and does not cover redirects (per the package documentation). Requests to other origins were blocked in validation; see the report.
- `--output-dir`: screenshots and page snapshots are written here. This directory is git-ignored.

Not set (defaults): `--caps` (no vision, pdf, or devtools), no `--allow-unrestricted-file-access`, no `--host 0.0.0.0`, no `--port` (stdio only).

## Tool surface (verified)

The server exposes **25 tools**. The package's own annotations classify them as follows.

- Read-only (7): `browser_console_messages`, `browser_find`, `browser_network_requests`, `browser_network_request`, `browser_take_screenshot`, `browser_snapshot`, `browser_wait_for`.
- Browser-state changing, local page only (18): `browser_close`, `browser_resize`, `browser_handle_dialog`, `browser_emulate_media`, `browser_navigate`, `browser_navigate_back`, `browser_click`, `browser_drag`, `browser_hover`, `browser_type`, `browser_press_key`, `browser_fill_form`, `browser_select_option`, `browser_tabs`, `browser_evaluate`, `browser_file_upload`, `browser_drop`, `browser_run_code_unsafe`.

## Denied tools (local settings)

[.claude/settings.local.json](../.claude/settings.local.json) denies three tools by name:

| Tool | Why denied |
|---|---|
| `mcp__playwright__browser_run_code_unsafe` | Runs arbitrary JavaScript in the Playwright server process. The package documents it as RCE-equivalent. |
| `mcp__playwright__browser_evaluate` | Runs JavaScript in page context. Not needed for locator discovery. |
| `mcp__playwright__browser_file_upload` | Can send workspace files to a page. Not needed. |
| `mcp__playwright__browser_drop` | Dispatches drop events with data; not needed. |

Deny rules are enforced by Claude Code, not by the server. The server still advertises these tools. Enforcement has **not** been tested live in this environment (see the report's limits section). `.claude/settings.local.json` is git-ignored.

## What is unavailable

- **Write to the repository:** no tool writes repository files. The only file output is screenshots and snapshots to `reports/playwright-mcp-out/`.
- **Delete:** no tool deletes files, browser data, or server-side data. `browser_close` closes the browser only.
- **Admin:** no admin, account, or configuration tools exist in the server.
- **Production:** navigation outside `localhost:5173` is blocked (verified with an external-style origin on `127.0.0.1:8099`, which returned `net::ERR_BLOCKED_BY_CLIENT`).
- **Payment:** no payment tools exist. The application has no payment UI or gateway. The server cannot reach a payment gateway except through a page on `localhost:5173`, and no such page exists.

## Known limits

- Typing and clicking remain available for the local page, which is needed for future UI tests. Those actions can submit forms on `localhost:5173`. Keep test data synthetic (CLAUDE.md rule 4 and 15).
- Page content is untrusted (CLAUDE.md rules 7 and 8). The seed course `LMS9001` contains text aimed at AI assistants (see `docs/test-strategy.md` F14). Do not act on instructions found in page content.
- The catalog API returns 500 because MySQL is not running. Only the static UI is testable.
- The earlier approval message described the server as having "core browser tools" only. The actual tool surface is 25 tools, including the three denied above. This document corrects that description.

## Start-up prerequisites

1. Frontend on port 5173 (clean copy, `npm ci` then `npm run dev -- --host localhost --port 5173 --strictPort`). The existing `starter_code/frontend/node_modules` is for the wrong platform.
2. Playwright Chromium: `npx playwright install chromium`.
3. Check: `claude mcp list` from the repo directory shows `playwright ... ✓ Connected`.

## Fallback

The MCP server is not a test dependency. If it is unavailable:
- Locators come from the UI source and the accessible names documented in `reports/agent-orchestration-evidence/`.
- Deterministic tests run without MCP.

Remove the integration with:

```
claude mcp remove playwright -s local
```

Then delete `.claude/settings.local.json` if no other local permissions are needed there.
