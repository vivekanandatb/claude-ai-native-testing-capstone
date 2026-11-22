"""Load Markdown knowledge documents and parse their front matter."""
import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

REQUIRED_FIELDS = ("doc_id", "title", "version", "classification")


@dataclass
class Document:
    source_file: str      # path relative to the knowledge directory
    doc_id: str
    title: str
    version: str
    status: str
    classification: str
    text: str             # full file text; all character ranges refer to this
    body_start: int       # offset where the body (after front matter) begins
    sha256: str


def _parse_front_matter(text):
    m = re.match(r"---\n(.*?)\n---\n", text, re.S)
    if not m:
        raise ValueError("missing front matter")
    fields = dict(re.findall(r"^(\w+):\s*(.*)$", m.group(1), re.M))
    return fields, m.end()


def load_documents(knowledge_dir):
    docs = []
    for path in sorted(Path(knowledge_dir).glob("*.md")):
        text = path.read_text(encoding="utf-8")
        fields, body_start = _parse_front_matter(text)
        missing = [f for f in REQUIRED_FIELDS if f not in fields]
        if missing:
            raise ValueError(f"{path.name}: missing front matter fields {missing}")
        docs.append(Document(
            source_file=path.name,
            doc_id=fields["doc_id"],
            title=fields["title"],
            version=fields["version"],
            status=fields.get("status", "unknown"),
            classification=fields["classification"],
            text=text,
            body_start=body_start,
            sha256=hashlib.sha256(text.encode("utf-8")).hexdigest(),
        ))
    if not docs:
        raise ValueError(f"no Markdown documents found in {knowledge_dir}")
    return docs
