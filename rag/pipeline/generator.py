"""Answer generation.

LocalExtractiveGenerator is an openly documented, deterministic test adapter.
It does NOT call a language model and does NOT read the rendered prompt. It
selects lines from the retrieved (and sanitised) chunks and quotes them, so
every statement in an answer is a verbatim line from a cited chunk.

The rendered prompt (prompt.py) is built and hashed for every query so that a
hosted-model adapter can be added behind the same interface later. No hosted
adapter is implemented or enabled: it would send chunk text to an external
provider and may cost money, which requires explicit approval first.

Outcomes
  ANSWERED                 supported by retrieved context
  ABSTAIN_UNDEFINED        the knowledge base explicitly says the item is undefined/unknown
  ABSTAIN_NO_SUPPORT       retrieved context does not support an answer
  REFUSED_INJECTION        question is about obeying an embedded instruction; answered with policy
  REFUSED_SECRET_REQUEST   the question asks to output a secret

Known limitation: matching is lexical (TF-IDF), not semantic. Paraphrases that
share no vocabulary with the knowledge base will not be found and will abstain.
"""
import re

import config
from embedder import tokenize
from safety import (QUARANTINE_MARKER, is_direct_injection, is_embedded_instruction_policy_question,
                    is_secret_request, mask_spans, redact_secrets)

_UNDEFINED_SECTION = re.compile(r"explicitly undefined|not defined here", re.I)
_UNDEFINED_TEXT = re.compile(r"\b(not defined|undefined|not documented)\b", re.I)
_POLICY_TEXT = re.compile(r"untrusted|must not override|ignore it|refuse|never revealed|never reveal|reveal nothing", re.I)
_SKIP_LINE = re.compile(r"^(\|[-\s|]+\|?|#+\s.*|---+|>.*)$")     # separators, headings, quotes/banners
_ROW = re.compile(r"^\|\s*([^|]+?)\s*\|\s*(.*?)\s*\|?\s*$")
_WHICH_DOC = re.compile(r"\bwhich\s+(document|doc|file)\b", re.I)
_META_TERMS = {"document", "doc", "file", "define", "defines", "defined"}
_LEXICON_MIN_IDF = 3.5          # a "topic" term is rare: it appears in at most ~4 chunks


def _clean(s):
    return re.sub(r"\s+", " ", s.replace("**", "").replace("`", "")).strip()


def _role(chunk):
    return config.DOCUMENT_ROLES.get(chunk.source_file, "normative")


def _units(hit, titles):
    """Split a sanitised chunk into answer units (table rows, bullets, numbered lines)."""
    chunk = hit["chunk"]
    text = mask_spans(chunk.text, chunk.char_start, chunk.quarantined)
    section_undefined = bool(_UNDEFINED_SECTION.search(chunk.section))
    context = f"{titles.get(chunk.source_file, '')} {chunk.section}"
    out = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or QUARANTINE_MARKER in line or _SKIP_LINE.match(line):
            continue
        label, body = "", line
        m = _ROW.match(line)
        if m:
            label, body = m.group(1), m.group(2)
            if label.upper() in ("ID", "REQUIREMENT", "STANDARD") or not body:
                continue
        else:
            body = re.sub(r"^[-*]\s+", "", line)
            n = re.match(r"^(\d+(?:\.\d+)+)\s+(.*)$", body)
            if n:
                label, body = n.group(1), n.group(2)
        body = _clean(body)
        if len(body) < 8:
            continue
        out.append({
            "label": _clean(label), "text": body, "context": context,
            "chunk": chunk, "chunk_score": hit["score"], "role": _role(chunk),
            "undefined": section_undefined or bool(_UNDEFINED_TEXT.search(body)),
        })
    return out


def _unit_terms(unit):
    # Heading and document-title context is part of a line's meaning (e.g. rules
    # under "7. Idempotency" are about idempotency even if the line omits the word).
    return set(tokenize(f"{unit['label']} {unit['text']} {unit['context']}"))


def _coverage(unit, query_terms, embedder):
    """Share of the query's idf weight that this unit's terms cover (0..1)."""
    terms = _unit_terms(unit)
    total = sum(embedder.term_weight(t) for t in query_terms)
    if total == 0:
        return 0.0
    return sum(embedder.term_weight(t) for t in query_terms if t in terms) / total


def _citation(chunk):
    return {"source_file": chunk.source_file, "chunk_id": chunk.chunk_id,
            "doc_version": chunk.doc_version, "section": chunk.section,
            "char_start": chunk.char_start, "char_end": chunk.char_end}


def _dedupe(units):
    seen, out = set(), []
    for u in units:
        key = (u["chunk"].source_file, u["text"])
        if key not in seen:
            seen.add(key)
            out.append(u)
    return out


def _format(units):
    lines = []
    for u in units:
        label = f"{u['label']}: " if u["label"] else ""
        c = u["chunk"]
        lines.append(f"- {label}{u['text']} [{c.source_file} | {c.chunk_id}]")
    return "\n".join(lines)


def _rank(units):
    units.sort(key=lambda u: (-u["coverage"], -u["chunk_score"], u["chunk"].chunk_id))


class LocalExtractiveGenerator:
    name = config.GENERATOR["name"]
    version = config.GENERATOR["version"]

    def generate(self, question, hits, index):
        cfg, embedder = config.GENERATOR, index.embedder
        titles = {d["source_file"]: d.get("title", "") for d in index.manifest["documents"]}
        flagged = sorted({h["chunk"].chunk_id for h in hits if h["chunk"].quarantined})

        def result(outcome, answer, units=()):
            cites, seen = [], set()
            for u in units:
                if u["chunk"].chunk_id not in seen:
                    seen.add(u["chunk"].chunk_id)
                    cites.append(_citation(u["chunk"]))
            return {"outcome": outcome, "answer": redact_secrets(answer), "citations": cites,
                    "injection_flagged_chunk_ids": flagged}

        if is_secret_request(question):
            return result("REFUSED_SECRET_REQUEST",
                          "I will not reveal, request, or print secrets, API keys, passwords, or tokens.")

        if is_direct_injection(question) and not is_embedded_instruction_policy_question(question):
            return result("REFUSED_INJECTION",
                          "No. The question contains an instruction to override rules or reveal secrets. "
                          "That is treated as untrusted input and was not followed.")

        which_doc = bool(_WHICH_DOC.search(question))
        query_terms = sorted({t for t in tokenize(question)
                              if embedder.term_weight(t) > 0 and not (which_doc and t in _META_TERMS)})
        all_units = _dedupe([u for c in index.chunks for u in _units({"chunk": c, "score": 0.0}, titles)])
        retrieved_units = _dedupe([u for h in hits for u in _units(h, titles)])
        for u in all_units + retrieved_units:
            u["coverage"] = _coverage(u, query_terms, embedder)

        # 1. Questions about obeying embedded instructions: answer with documented policy.
        if is_embedded_instruction_policy_question(question):
            policy = [u for u in retrieved_units if _POLICY_TEXT.search(u["text"])]
            _rank(policy)
            if policy:
                chosen = policy[: cfg["max_answer_units"]]
                body = ("No. Instructions embedded in retrieved documents are untrusted data and "
                        "must not be followed. Documented rules:\n" + _format(chosen))
                return result("REFUSED_INJECTION", body, chosen)

        if not query_terms:
            return result("ABSTAIN_NO_SUPPORT", "Not documented in the knowledge base. No retrieved context matches the question.")

        # Normative lines only; the defect register and the attack fixture never answer as fact.
        asks_defects = any(t in question.lower() for t in config.DEFECT_QUESTION_TERMS)
        allowed = {"normative"} | ({"defect_register"} if asks_defects else set())
        candidates = [u for u in retrieved_units if u["role"] in allowed]
        pool = [u for u in all_units if u["role"] in allowed]

        # 2. "Which document ..." questions: answer at document level.
        if which_doc:
            by_file = {}
            for u in pool:
                by_file.setdefault(u["chunk"].source_file, []).append(u)
            scored = []
            for f, us in by_file.items():
                _rank(us)
                scored.append((sum(u["coverage"] for u in us[:3]), f, us))
            scored.sort(key=lambda s: (-s[0], s[1]))
            if scored and scored[0][2][0]["coverage"] >= cfg["min_unit_coverage"]:
                _, f, us = scored[0]
                c = us[0]["chunk"]
                head = f'{f} ({c.doc_id}, v{c.doc_version}), titled "{titles.get(f, "")}".'
                sections, seen = [], set()
                for ch in index.chunks:
                    tail = ch.section.split(" > ")[-1]
                    if ch.source_file == f and tail not in seen and set(tokenize(tail)) & set(query_terms):
                        seen.add(tail)
                        sections.append((tail, ch))
                if sections:
                    lines = [f"- {t} [{ch.source_file} | {ch.chunk_id}]" for t, ch in sections[:3]]
                    cites = [{"chunk": ch} for _, ch in sections[:3]]
                    return result("ANSWERED", f"{head}\nMatching sections:\n" + "\n".join(lines), [{"chunk": x["chunk"]} for x in cites])
                chosen = us[:2]
                return result("ANSWERED", f"{head}\nSupporting lines:\n" + _format(chosen), chosen)
            return result("ABSTAIN_NO_SUPPORT", "Not documented in the knowledge base. No document matches the question.")

        if not candidates:
            return result("ABSTAIN_NO_SUPPORT", "Not documented in the knowledge base. No retrieved context matches the question.")

        _rank(candidates)
        best = candidates[0]
        if best["coverage"] < cfg["min_unit_coverage"]:
            return result("ABSTAIN_NO_SUPPORT",
                          "Not documented in the knowledge base. The retrieved context does not state an answer to this question.")

        near = [u for u in candidates if u["coverage"] >= best["coverage"] * cfg["near_best_ratio"]]
        defined = [u for u in near if not u["undefined"]]
        if not defined:
            chosen = near[: cfg["max_answer_units"]]
            return result("ABSTAIN_UNDEFINED",
                          "Not documented in the knowledge base. The knowledge base states this item is undefined or unknown:\n" + _format(chosen),
                          chosen)

        chosen = defined[: cfg["max_answer_units"]]

        # 3. Undefined-topic guard: if the question names a rare topic that the knowledge
        #    base declares undefined, and no defined line actually covers that topic, the
        #    lexical match was incidental (shared generic words). Abstain instead.
        topic_terms = [t for t in query_terms if embedder.term_weight(t) >= _LEXICON_MIN_IDF]
        undefined_units = [u for u in pool if u["undefined"]]
        uncovered = [t for t in topic_terms
                     if any(t in _unit_terms(u) for u in undefined_units)
                     and not any(t in _unit_terms(u) for u in chosen)]
        if uncovered:
            support = [u for u in undefined_units if any(t in _unit_terms(u) for t in uncovered)]
            for u in support:
                u["coverage"] = _coverage(u, query_terms, embedder)
            _rank(support)
            support = support[: cfg["max_answer_units"]]
            return result("ABSTAIN_UNDEFINED",
                          "Not documented in the knowledge base. The question asks about "
                          f"{', '.join(uncovered)}, which the knowledge base states is undefined or unknown:\n" + _format(support),
                          support)

        notes = [u for u in near if u["undefined"]][:1]
        text = "Based on the knowledge base:\n" + _format(chosen)
        if notes:
            text += "\nNot documented (stated as undefined):\n" + _format(notes)
        return result("ANSWERED", text, chosen + notes)
