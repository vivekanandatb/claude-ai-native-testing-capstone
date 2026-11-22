#!/usr/bin/env python3
"""Redact secret-like values from text test artifacts before they are uploaded.

Known source: Spring Boot prints "Using generated security password: <value>" into the
Surefire output that is embedded in the XML reports. Other generic patterns are covered
too. Only text files are rewritten; binary files (traces, PNG screenshots) are skipped
and must not contain credentials because the UI suite uses synthetic data only.

Prints the number of redactions per file, never the redacted values.
Usage: python3 -I scripts/ci_sanitize_reports.py <dir> [<dir> ...]
"""
import os
import re
import sys

TEXT_SUFFIXES = (".xml", ".txt", ".log", ".json", ".jsonl", ".csv", ".md", ".yml", ".yaml")

PATTERNS = [
    (re.compile(r"(?i)(generated security password)\s*:\s*\S+"), r"\1: [REDACTED]"),
    (re.compile(r"(?i)\b(password|passwd|secret|token|api[_-]?key)(\s*[:=]\s*)[^\s<\"',;]+"), r"\1\2[REDACTED]"),
    (re.compile(r"sk-[A-Za-z0-9_-]{10,}"), "[REDACTED]"),
    (re.compile(r"AKIA[0-9A-Z]{12,}"), "[REDACTED]"),
    (re.compile(r"ghp_[A-Za-z0-9]{20,}"), "[REDACTED]"),
    (re.compile(r"eyJ[A-Za-z0-9_-]{20,}"), "[REDACTED]"),
]


def sanitize_file(path: str) -> int:
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    total = 0
    for pattern, replacement in PATTERNS:
        text, count = pattern.subn(replacement, text)
        total += count
    if total:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
    return total


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: ci_sanitize_reports.py <dir> [<dir> ...]", file=sys.stderr)
        return 2
    grand_total = 0
    for root_dir in sys.argv[1:]:
        for dirpath, _dirs, files in os.walk(root_dir):
            for name in files:
                if not name.lower().endswith(TEXT_SUFFIXES):
                    continue
                path = os.path.join(dirpath, name)
                count = sanitize_file(path)
                if count:
                    grand_total += count
                    print(f"redacted {count} value(s) in {path}")
    print(f"sanitize complete: {grand_total} redaction(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
