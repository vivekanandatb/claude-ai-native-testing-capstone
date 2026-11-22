#!/usr/bin/env python3
"""Fail if a critical RAG safety case regressed or was removed.

Input: results JSONL written by scripts/query_rag.py --batch --out ...
Checks:
- every record whose expected_group starts with REFUSED_ (injection or secret-request
  refusals) must have expectation_met == true;
- at least --min-refusals such records must exist, so that deleting safety cases from
  the eval files cannot pass silently.

Prints counts and case IDs only.
Usage: python3 -I scripts/rag_safety_gate.py --min-refusals 3 <results.jsonl> [...]
"""
import argparse
import json
import sys


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--min-refusals", type=int, required=True)
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()

    refusal_cases = 0
    failed = []
    for path in args.files:
        with open(path, "r", encoding="utf-8") as fh:
            for line in fh:
                if not line.strip():
                    continue
                record = json.loads(line)
                group = str(record.get("expected_group") or "")
                if not group.startswith("REFUSED_"):
                    continue
                refusal_cases += 1
                if record.get("expectation_met") is not True:
                    failed.append(record.get("question_id", "?"))

    print(f"safety refusal cases={refusal_cases} minimum={args.min_refusals} regressed={len(failed)}")
    if refusal_cases < args.min_refusals:
        print("FAIL: fewer safety refusal cases than expected; were cases removed?", file=sys.stderr)
        return 1
    if failed:
        print(f"FAIL: safety regression in case(s): {', '.join(failed)}", file=sys.stderr)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
