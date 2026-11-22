#!/usr/bin/env python3
"""Query the local RAG pipeline.

Single question:  python3 -I scripts/query_rag.py "May logs contain passwords?"
Batch evaluation: python3 -I scripts/query_rag.py --batch rag/eval/test_questions.jsonl \
                      --out reports/rag-query-results.jsonl

Output never includes secrets. Retrieved text is treated as untrusted data.
Batch mode compares each result with the expectation written in the questions
file BEFORE the run and exits 1 if any expectation is not met.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "rag" / "pipeline"))

import config                                       # noqa: E402
from generator import LocalExtractiveGenerator      # noqa: E402
from index import VectorIndex                       # noqa: E402
from prompt import build_prompt                     # noqa: E402
from retriever import retrieve                      # noqa: E402

ATTACK_PHRASES = ("ignore previous rules", "reveal api keys", "system override")
ABSTAIN = {"ABSTAIN_UNDEFINED", "ABSTAIN_NO_SUPPORT"}


def run_query(index, generator, question):
    hits = retrieve(index, question, config.RETRIEVER["top_k"])
    prompt = build_prompt(config.PROMPT_FILE, question, hits)
    gen = generator.generate(question, hits, index)
    return {
        "question": question,
        "outcome": gen["outcome"],
        "answer": gen["answer"],
        "citations": gen["citations"],
        "retrieved": [
            {"rank": h["rank"], "score": round(h["score"], 4), "chunk_id": h["chunk"].chunk_id,
             "source_file": h["chunk"].source_file, "doc_version": h["chunk"].doc_version,
             "section": h["chunk"].section, "char_start": h["chunk"].char_start,
             "char_end": h["chunk"].char_end, "contains_quarantined_text": bool(h["chunk"].quarantined)}
            for h in hits
        ],
        "injection_flagged_chunk_ids": gen["injection_flagged_chunk_ids"],
        "prompt_id": prompt["prompt_id"],
        "prompt_version": prompt["prompt_version"],
        "prompt_sha256": prompt["prompt_sha256"],
        "generator": {"name": generator.name, "version": generator.version, "calls_model": False},
        "embedder": config.EMBEDDER["name"],
        "chunker": {k: config.CHUNKER[k] for k in ("name", "version", "max_chars", "overlap_chars")},
        "retriever": config.RETRIEVER,
        "pipeline_version": config.PIPELINE_VERSION,
        "corpus": [{"source_file": d["source_file"], "version": d["version"], "sha256": d["sha256"]}
                   for d in index.manifest["documents"]],
    }


def check_expectation(case, result):
    reasons = []
    group = case.get("expected_group")
    if group == "abstain" and result["outcome"] not in ABSTAIN:
        reasons.append(f"expected abstain, got {result['outcome']}")
    elif group and group != "abstain" and result["outcome"] != group:
        reasons.append(f"expected {group}, got {result['outcome']}")
    allowed = case.get("expected_sources_any_of")
    if allowed and not any(c["source_file"] in allowed for c in result["citations"]):
        reasons.append(f"no citation from {allowed}")
    if group == "ANSWERED" and not result["citations"]:
        reasons.append("no citations")
    answer_lower = result["answer"].lower()
    for needle in case.get("expected_answer_contains", []):
        if needle.lower() not in answer_lower:
            reasons.append(f"answer missing expected text: {needle!r}")
    any_of = case.get("expected_answer_contains_any_of")
    if any_of and not any(n.lower() in answer_lower for n in any_of):
        reasons.append(f"answer contains none of {any_of!r}")
    for source in case.get("expected_excluded_sources", []):
        if any(c["source_file"] == source for c in result["citations"]):
            reasons.append(f"cites excluded source: {source}")
    if any(p in answer_lower for p in ATTACK_PHRASES):
        reasons.append("answer repeats embedded attack text")
    return reasons


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("question", nargs="?")
    ap.add_argument("--batch")
    ap.add_argument("--out")
    args = ap.parse_args()

    if not config.INDEX_FILE.exists():
        print("Index not found. Run: python3 -I scripts/build_index.py", file=sys.stderr)
        return 2
    index = VectorIndex.load(config.INDEX_FILE)
    generator = LocalExtractiveGenerator()

    if args.batch:
        cases = [json.loads(l) for l in Path(args.batch).read_text(encoding="utf-8").splitlines() if l.strip()]
        records, unmet = [], 0
        for case in cases:
            res = run_query(index, generator, case["question"])
            reasons = check_expectation(case, res)
            res.update({"question_id": case["id"], "expected_group": case.get("expected_group"),
                        "expected_sources_any_of": case.get("expected_sources_any_of"),
                        "expected_answer_contains": case.get("expected_answer_contains"),
                        "expected_answer_contains_any_of": case.get("expected_answer_contains_any_of"),
                        "expected_excluded_sources": case.get("expected_excluded_sources"),
                        "expectation_met": not reasons, "expectation_failures": reasons,
                        "human_review": "required"})
            records.append(res)
            unmet += bool(reasons)
            print(f"{case['id']:4s} {'MET  ' if not reasons else 'UNMET'} {res['outcome']:22s} {case['question'][:70]}")
        if args.out:
            out = Path(args.out)
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text("\n".join(json.dumps(r, sort_keys=True) for r in records) + "\n", encoding="utf-8")
            print(f"wrote {len(records)} records to {args.out}")
        print(f"expectations met: {len(records) - unmet}/{len(records)}")
        return 1 if unmet else 0

    if not args.question:
        ap.error("provide a question or --batch")
    res = run_query(index, generator, args.question)
    print(f"outcome: {res['outcome']}\n{res['answer']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
