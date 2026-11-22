#!/usr/bin/env python3
"""Fail the CI job unless enough tests were collected and none failed.

Reads JUnit XML (pytest --junitxml) or Surefire XML (TEST-*.xml) files or directories.
Exits 1 when the summed test count is below --min, when no report exists, or when any
failure or error is recorded. Prints counts only, never report contents.

Usage: python3 -I scripts/ci_check_test_count.py --min 4 test-results/junit-ui.xml
"""
import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET


def report_files(paths):
    files = []
    for path in paths:
        if os.path.isdir(path):
            files.extend(sorted(glob.glob(os.path.join(path, "TEST-*.xml"))))
        elif os.path.isfile(path):
            files.append(path)
        else:
            print(f"report path not found: {path}", file=sys.stderr)
    return files


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--min", type=int, required=True, help="minimum number of tests expected")
    ap.add_argument("paths", nargs="+")
    args = ap.parse_args()

    files = report_files(args.paths)
    if not files:
        print("FAIL: no test report files found", file=sys.stderr)
        return 1

    tests = failures = errors = 0
    for path in files:
        for suite in ET.parse(path).getroot().iter("testsuite"):
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0))
            errors += int(suite.get("errors", 0))

    print(f"reports={len(files)} collected={tests} minimum={args.min} failures={failures} errors={errors}")
    if tests == 0:
        print("FAIL: zero tests were collected", file=sys.stderr)
        return 1
    if tests < args.min:
        print(f"FAIL: collected {tests} tests, expected at least {args.min}", file=sys.stderr)
        return 1
    if failures or errors:
        print("FAIL: test failures or errors recorded", file=sys.stderr)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
