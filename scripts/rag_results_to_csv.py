#!/usr/bin/env python3
"""Convert query_rag.py batch JSONL output to a flat CSV for review.

Usage: python3 -I scripts/rag_results_to_csv.py <results.jsonl> <results.csv>

Columns are chosen for human review. Retrieved chunk text and full answers are not
exported here; the JSONL file keeps the complete record. Every row keeps
human_review=required, because no answer in this repository has been reviewed by a human.
"""
import csv
import json
import sys

COLUMNS = [
    "question_id",
    "expected_group",
    "outcome",
    "expectation_met",
    "expectation_failures",
    "citations",
    "prompt_id",
    "prompt_version",
    "pipeline_version",
    "human_review",
]


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: rag_results_to_csv.py <results.jsonl> <results.csv>", file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    rows = 0
    with open(src, "r", encoding="utf-8") as fin, open(dst, "w", encoding="utf-8", newline="") as fout:
        writer = csv.DictWriter(fout, fieldnames=COLUMNS)
        writer.writeheader()
        for line in fin:
            if not line.strip():
                continue
            record = json.loads(line)
            citations = sorted({c["source_file"] for c in record.get("citations", [])})
            writer.writerow({
                "question_id": record.get("question_id", ""),
                "expected_group": record.get("expected_group", ""),
                "outcome": record.get("outcome", ""),
                "expectation_met": record.get("expectation_met", ""),
                "expectation_failures": "; ".join(record.get("expectation_failures", [])),
                "citations": "; ".join(citations),
                "prompt_id": record.get("prompt_id", ""),
                "prompt_version": record.get("prompt_version", ""),
                "pipeline_version": record.get("pipeline_version", ""),
                "human_review": record.get("human_review", "required"),
            })
            rows += 1
    print(f"wrote {rows} row(s) to {dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
