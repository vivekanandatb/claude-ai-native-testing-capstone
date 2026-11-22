#!/usr/bin/env node
/*
 * postToolUse hook: write one line per tool call to agent-audit.log.
 *
 * The application already has CatalogAuditLogger, which records what users do
 * to the catalogue. This records what the agent does to the repository. Same
 * idea, one level up.
 *
 * A postToolUse hook cannot change what already happened -- the tool has run.
 * Its output is ignored. All it can do is observe, so keep it fast: this hook
 * appends one line and exits.
 */

const fs = require("fs");
const path = require("path");

const LOG_NAME = "agent-audit.log";

function readStdin() {
  try {
    return fs.readFileSync(0, "utf8");
  } catch {
    return "";
  }
}

// Keep the line to one line, and short enough to scan a hundred of them.
function summarise(toolName, args) {
  if (!args || typeof args !== "object") return "";

  const interesting = [
    "command",
    "filePath",
    "file_path",
    "path",
    "query",
    "sql",
    "pattern",
    "url",
    "table",
  ];

  for (const key of interesting) {
    if (typeof args[key] === "string" && args[key].trim()) {
      return `${key}=${flatten(args[key])}`;
    }
  }

  const firstString = Object.entries(args).find(
    ([, v]) => typeof v === "string" && v.trim(),
  );
  return firstString ? `${firstString[0]}=${flatten(firstString[1])}` : "";
}

function flatten(s) {
  const oneLine = s.replace(/\s+/g, " ").trim();
  return oneLine.length > 120 ? `${oneLine.slice(0, 117)}...` : oneLine;
}

const raw = readStdin();
if (!raw.trim()) process.exit(0);

let payload;
try {
  payload = JSON.parse(raw);
} catch {
  process.exit(0);
}

const toolName = payload.toolName || payload.tool_name || "unknown";
const toolArgs = payload.toolArgs || payload.tool_input || {};
const result = payload.toolResult || payload.tool_result || {};
const outcome = result.resultType || result.result_type || "unknown";
const session = String(payload.sessionId || payload.session_id || "-").slice(
  0,
  8,
);
const cwd = payload.cwd || process.cwd();

const line =
  [
    new Date().toISOString(),
    `session=${session}`,
    `tool=${toolName}`,
    `outcome=${outcome}`,
    summarise(toolName, toolArgs),
  ]
    .filter(Boolean)
    .join("  ") + "\n";

try {
  fs.appendFileSync(path.join(cwd, LOG_NAME), line);
} catch {
  // An audit hook that breaks the session is worse than a missing line.
}

process.exit(0);
