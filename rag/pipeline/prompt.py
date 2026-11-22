"""Build the versioned grounded prompt. Retrieved text is wrapped as untrusted data."""
import hashlib

from safety import mask_spans


def load_template(path):
    raw = open(path, encoding="utf-8").read()
    header, _, body = raw.partition("\n---\n")
    meta = dict(line.split(": ", 1) for line in header.splitlines() if ": " in line)
    return meta.get("prompt_id", "unknown"), meta.get("prompt_version", "unknown"), body


def render_context(hit):
    chunk = hit["chunk"]
    safe_text = mask_spans(chunk.text, chunk.char_start, chunk.quarantined)
    return (
        f'<chunk id="{chunk.chunk_id}" source="{chunk.source_file}" version="{chunk.doc_version}" '
        f'section="{chunk.section}" chars="{chunk.char_start}-{chunk.char_end}" '
        f'score="{hit["score"]:.4f}" trust="untrusted">\n{safe_text}\n</chunk>'
    )


def build_prompt(template_path, question, hits):
    prompt_id, version, body = load_template(template_path)
    contexts = "\n\n".join(render_context(h) for h in hits) or "(no context retrieved)"
    text = body.replace("{question}", question).replace("{contexts}", contexts)
    return {
        "prompt_id": prompt_id,
        "prompt_version": version,
        "prompt_sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
        "text": text,
    }
