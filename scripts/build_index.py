#!/usr/bin/env python3
"""Build the local vector index over rag/knowledge.

Usage: python3 -I scripts/build_index.py

Checks performed on every build (exit code 1 if any fails):
  - each chunk's text equals document.text[char_start:char_end]
  - chunk IDs are unique
  - chunk IDs are stable: a second, independent chunking pass yields identical IDs
  - chunks overlap within a section (reported, not enforced across sections)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "rag" / "pipeline"))

import config                                  # noqa: E402
from chunker import chunk_document             # noqa: E402
from index import VectorIndex                  # noqa: E402
from loader import load_documents              # noqa: E402


def main():
    docs = load_documents(config.KNOWLEDGE_DIR)
    by_name = {d.source_file: d for d in docs}
    chunks = []
    for d in docs:
        chunks.extend(chunk_document(d, config.CHUNKER["max_chars"], config.CHUNKER["overlap_chars"]))

    failures = []
    for c in chunks:
        if by_name[c.source_file].text[c.char_start:c.char_end] != c.text:
            failures.append(f"range mismatch: {c.chunk_id}")
    ids = [c.chunk_id for c in chunks]
    if len(ids) != len(set(ids)):
        failures.append("duplicate chunk IDs")
    again = [c.chunk_id for d in docs for c in chunk_document(d, config.CHUNKER["max_chars"], config.CHUNKER["overlap_chars"])]
    if again != ids:
        failures.append("chunk IDs are not stable across two passes")

    overlaps = 0
    for a, b in zip(chunks, chunks[1:]):
        if a.source_file == b.source_file and a.section == b.section and b.char_start < a.char_end:
            overlaps += 1

    index = VectorIndex.build(docs, chunks)
    index.save(config.INDEX_FILE)

    print(f"documents:        {len(docs)}")
    for d in docs:
        n = sum(1 for c in chunks if c.source_file == d.source_file)
        print(f"  {d.source_file:32s} {d.doc_id:14s} v{d.version}  chunks={n}")
    print(f"chunks:           {len(chunks)}")
    print(f"overlapping pairs:{overlaps}")
    print(f"quarantine chunks:{sum(1 for c in chunks if c.quarantined)}")
    print(f"vocab size:       {index.manifest['vocab_size']}")
    print(f"chunker:          {config.CHUNKER}")
    print(f"embedder:         {config.EMBEDDER['name']} v{config.EMBEDDER['version']}")
    print(f"index file:       {config.INDEX_FILE.relative_to(config.ROOT)}")
    if failures:
        print("BUILD CHECKS FAILED:")
        for f in failures:
            print("  -", f)
        return 1
    print("build checks:     PASS (ranges, unique IDs, stable IDs)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
