"""Central, versioned configuration for the local RAG pipeline.

Every value that can change results lives here and is written into the index
manifest and every query result, so runs are reproducible and comparable.
"""
from pathlib import Path

PIPELINE_VERSION = "1.1.0"

ROOT = Path(__file__).resolve().parents[2]
KNOWLEDGE_DIR = ROOT / "rag" / "knowledge"
INDEX_DIR = ROOT / "rag" / "index"          # generated; excluded from Git
INDEX_FILE = INDEX_DIR / "index.json"
PROMPT_FILE = ROOT / "rag" / "prompts" / "grounded_qa.txt"

CHUNKER = {
    "name": "section-line-window",
    "version": "1.0.0",
    "max_chars": 700,
    "overlap_chars": 150,
}

EMBEDDER = {
    "name": "tfidf-sublinear-l2",
    "version": "1.0.0",
    "note": "Local lexical embedding (stdlib only). Not a neural embedding model.",
}

RETRIEVER = {
    "name": "brute-force-cosine",
    "version": "1.0.0",
    "top_k": 5,
}

# Document roles. Only "normative" documents may serve as authoritative answers.
#   defect_register: describes WRONG behavior (defects). Quoting it as fact would mislead,
#                    so it is used only when the question is about defects/regressions.
#   test_fixture:    hostile sample content. Used only to explain how embedded
#                    instructions must be handled, never as a source of facts.
# Kept here (not in the documents) so the knowledge base files stay unchanged.
DOCUMENT_ROLES = {
    "known_defects.md": "defect_register",
    "malicious_document_sample.md": "test_fixture",
}
DEFECT_QUESTION_TERMS = ("defect", "bug", "regression", "known issue", "known defect")

GENERATOR = {
    "name": "local-extractive",
    "version": "1.1.0",
    "note": "Deterministic extractive adapter. Does not call any model or network.",
    "min_unit_coverage": 0.40,      # share of query-term weight a line must cover
    "near_best_ratio": 0.75,        # lines within this ratio of the best are used
    "max_answer_units": 3,
}
