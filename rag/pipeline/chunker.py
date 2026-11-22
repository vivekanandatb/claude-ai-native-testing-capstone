"""Split documents into traceable, overlapping chunks with stable IDs.

Chunks never cross a section boundary. Within a section, whole lines are packed
up to max_chars and consecutive chunks overlap by at least overlap_chars. Each
chunk records its source file, document version, section path, and the exact
character range in the original file, so text == document.text[start:end].
"""
import hashlib
import re
from dataclasses import dataclass, field

from safety import quarantine_spans

_HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*$")


@dataclass
class Chunk:
    chunk_id: str
    source_file: str
    doc_id: str
    doc_version: str
    section: str
    char_start: int
    char_end: int
    text: str
    quarantined: list = field(default_factory=list)   # absolute (start, end) spans

    def to_dict(self):
        return {
            "chunk_id": self.chunk_id, "source_file": self.source_file,
            "doc_id": self.doc_id, "doc_version": self.doc_version,
            "section": self.section, "char_start": self.char_start,
            "char_end": self.char_end, "text": self.text,
            "quarantined": [list(s) for s in self.quarantined],
        }

    @classmethod
    def from_dict(cls, d):
        return cls(d["chunk_id"], d["source_file"], d["doc_id"], d["doc_version"],
                   d["section"], d["char_start"], d["char_end"], d["text"],
                   [tuple(s) for s in d.get("quarantined", [])])


def _sections(doc):
    """Yield (section_path, [(line_start, line_end), ...]) for the document body."""
    path, current, lines = [], None, []
    pos = 0
    for line in doc.text.splitlines(keepends=True):
        start, end = pos, pos + len(line)
        pos = end
        if start < doc.body_start:
            continue
        m = _HEADING.match(line.rstrip("\n"))
        if m:
            if current is not None and lines:
                yield current, lines
            level, title = len(m.group(1)), m.group(2)
            path = path[: level - 1] + [title]
            current, lines = " > ".join(path), []
        if current is None:
            current = doc.title
        lines.append((start, end))
    if current is not None and lines:
        yield current, lines


def _trim(doc, lines):
    """Drop leading/trailing blank lines; return None if nothing is left."""
    while lines and not doc.text[lines[0][0]:lines[0][1]].strip():
        lines = lines[1:]
    while lines and not doc.text[lines[-1][0]:lines[-1][1]].strip():
        lines = lines[:-1]
    return lines or None


def chunk_document(doc, max_chars, overlap_chars):
    spans = quarantine_spans(doc.text)
    chunks, index = [], 0
    for section, lines in _sections(doc):
        i = 0
        while i < len(lines):
            j, size = i, 0
            while j < len(lines):
                line_len = lines[j][1] - lines[j][0]
                if j > i and size + line_len > max_chars:
                    break
                size += line_len
                j += 1
            window = _trim(doc, lines[i:j])
            if window:
                start, end = window[0][0], window[-1][1]
                text = doc.text[start:end]
                digest = hashlib.sha1(
                    f"{doc.source_file}|{doc.version}|{start}|{end}|{text}".encode()
                ).hexdigest()[:8]
                chunks.append(Chunk(
                    chunk_id=f"{doc.doc_id}-{index:03d}-{digest}",
                    source_file=doc.source_file, doc_id=doc.doc_id,
                    doc_version=doc.version, section=section,
                    char_start=start, char_end=end, text=text,
                    quarantined=[s for s in spans if s[0] < end and s[1] > start],
                ))
                index += 1
            if j >= len(lines):
                break
            # Step back so the next chunk overlaps by >= overlap_chars, but always advance.
            back, k = 0, j
            while k - 1 > i and back < overlap_chars:
                k -= 1
                back += lines[k][1] - lines[k][0]
            i = max(k, i + 1)
    return chunks
