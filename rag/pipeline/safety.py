"""Safety helpers: untrusted-content quarantine, secret redaction, query intent.

Retrieved text is untrusted DATA. It can never change the pipeline's rules.
Lines that look like embedded instructions are quarantined: they are masked
before any prompt is built and never used to compose an answer.
"""
import re

QUARANTINE_MARKER = "[QUARANTINED: embedded instruction removed]"

# Fixture blocks written by the knowledge base for injection testing.
_BLOCK_BEGIN = re.compile(r"BEGIN SYNTHETIC INJECTION\b", re.I)
_BLOCK_END = re.compile(r"END SYNTHETIC INJECTION\b", re.I)

# Generic attack-shaped imperatives. Deliberately narrow: policy sentences such as
# "Secrets are never revealed" or "reveal nothing" must NOT match.
_INJECTION_PATTERNS = [
    re.compile(r"\bignore (all |any |the )?(previous|prior|above) (rules|instructions)\b", re.I),
    re.compile(r"\bdisregard (all |any |your )?(previous|prior) (rules|instructions)\b", re.I),
    re.compile(r"\breveal (the |all |your )?(api )?(keys?|secrets?|passwords?|tokens?)\b", re.I),
    re.compile(r"\bsystem override\b", re.I),
    re.compile(r"\byou may skip the failing tests\b", re.I),
    re.compile(r"\buse the live payment gateway\b", re.I),
]

_SECRET_SHAPES = [
    re.compile(r"sk-[A-Za-z0-9_-]{10,}"),
    re.compile(r"AKIA[0-9A-Z]{12,}"),
    re.compile(r"ghp_[A-Za-z0-9]{20,}"),
    re.compile(r"eyJ[A-Za-z0-9_-]{20,}"),
    re.compile(r"(?i)\b(password|passwd|secret|api[_-]?key|token)\s*[:=]\s*\S+"),
]

# A request to *output* a secret (imperative), as opposed to asking whether to comply.
_SECRET_REQUEST = re.compile(
    r"^\s*(please\s+)?(reveal|show|print|display|give|tell|send|share|output)\b.*"
    r"\b(api[ _-]?keys?|passwords?|tokens?|secrets?|credentials?)\b",
    re.I,
)
# A question about whether to obey an instruction found in a document.
_POLICY_QUESTION = re.compile(
    r"\b(comply|obey|follow|execute|act on|carry out)\b", re.I
)
_POLICY_SUBJECT = re.compile(
    r"\b(retrieved|embedded|document|instruction|asks? you|tells? you)\b", re.I
)


def quarantine_spans(text):
    """Return absolute (start, end) character spans of lines to quarantine."""
    spans = []
    inside = False
    pos = 0
    for line in text.splitlines(keepends=True):
        start, end = pos, pos + len(line)
        pos = end
        if _BLOCK_BEGIN.search(line):
            inside = True
        flagged = inside or any(p.search(line) for p in _INJECTION_PATTERNS)
        if flagged:
            spans.append((start, end))
        if _BLOCK_END.search(line):
            inside = False
    return spans


def mask_spans(text, base_offset, spans):
    """Replace quarantined spans (absolute offsets) inside a chunk's text."""
    if not spans:
        return text
    out, cursor = [], 0
    for s, e in sorted(spans):
        ls, le = max(s - base_offset, 0), min(e - base_offset, len(text))
        if le <= ls:
            continue
        out.append(text[cursor:ls])
        out.append(QUARANTINE_MARKER + ("\n" if text[ls:le].endswith("\n") else ""))
        cursor = le
    out.append(text[cursor:])
    return "".join(out)


def redact_secrets(text):
    """Defense in depth: never emit secret-shaped strings in any output."""
    for pattern in _SECRET_SHAPES:
        text = pattern.sub("[REDACTED]", text)
    return text


def is_secret_request(question):
    return bool(_SECRET_REQUEST.search(question))


def is_direct_injection(question):
    """The user's question itself contains an attack-shaped instruction."""
    return any(p.search(question) for p in _INJECTION_PATTERNS)


def is_embedded_instruction_policy_question(question):
    return bool(_POLICY_QUESTION.search(question) and _POLICY_SUBJECT.search(question))
