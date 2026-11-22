"""Local vector index: a single JSON file (no pickle, human-inspectable)."""
import json
from datetime import datetime, timezone
from pathlib import Path

import config
from chunker import Chunk
from embedder import TfidfEmbedder


class VectorIndex:
    def __init__(self, chunks, vectors, embedder, manifest):
        self.chunks, self.vectors, self.embedder, self.manifest = chunks, vectors, embedder, manifest

    @classmethod
    def build(cls, docs, chunks):
        embedder = TfidfEmbedder().fit([c.text for c in chunks])
        vectors = [embedder.embed(c.text) for c in chunks]
        manifest = {
            "pipeline_version": config.PIPELINE_VERSION,
            "built_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "chunker": config.CHUNKER,
            "embedder": config.EMBEDDER,
            "retriever": config.RETRIEVER,
            "documents": [
                {"source_file": d.source_file, "doc_id": d.doc_id, "title": d.title, "version": d.version,
                 "status": d.status, "classification": d.classification, "sha256": d.sha256}
                for d in docs
            ],
            "chunk_count": len(chunks),
            "vocab_size": len(embedder.vocab),
        }
        return cls(chunks, vectors, embedder, manifest)

    def search(self, query_vec, k):
        scored = []
        for chunk, vec in zip(self.chunks, self.vectors):
            small, big = (query_vec, vec) if len(query_vec) <= len(vec) else (vec, query_vec)
            score = sum(w * big.get(i, 0.0) for i, w in small.items())
            if score > 0:
                scored.append((score, chunk))
        scored.sort(key=lambda p: (-p[0], p[1].chunk_id))   # deterministic tie-break
        return scored[:k]

    def save(self, path):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "manifest": self.manifest,
            "embedder": self.embedder.to_dict(),
            "chunks": [c.to_dict() for c in self.chunks],
            "vectors": [{str(i): round(w, 8) for i, w in v.items()} for v in self.vectors],
        }
        path.write_text(json.dumps(payload, sort_keys=True), encoding="utf-8")

    @classmethod
    def load(cls, path):
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        return cls(
            [Chunk.from_dict(c) for c in data["chunks"]],
            [{int(i): w for i, w in v.items()} for v in data["vectors"]],
            TfidfEmbedder.from_dict(data["embedder"]),
            data["manifest"],
        )
