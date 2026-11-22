"""Local TF-IDF embedder (standard library only).

This is a lexical embedding, not a neural model. It is chosen because it needs
no downloads, no network, and no secrets, and it is fully deterministic.
Vectors are sparse {term_index: weight} dicts, L2-normalised, so the dot
product of two vectors is their cosine similarity.
"""
import math
import re
from collections import Counter

STOPWORDS = frozenset("""
a an and are as at be been but by can could did do does for from had has have how i if in into is it its
may me might must my no not of on or our shall should so than that the their then there these they this
those to us was we were what when where which who whom why will with would you your after before also
same
""".split())

_TOKEN = re.compile(r"[a-z0-9]+")


def _stem(tok):
    """Minimal, consistent suffix stripping so 'tests'/'test', 'charges'/'charge' match."""
    if len(tok) > 5 and tok.endswith("ies"):
        return tok[:-3] + "y"
    if len(tok) > 4 and tok.endswith("ing"):
        return tok[:-3]
    if len(tok) > 4 and tok.endswith("ed"):
        return tok[:-2]
    if len(tok) > 3 and tok.endswith("s") and not tok.endswith("ss"):
        return tok[:-1]
    return tok


def tokenize(text):
    return [_stem(t) for t in _TOKEN.findall(text.lower()) if t not in STOPWORDS and len(t) > 1]


class TfidfEmbedder:
    def __init__(self, vocab=None, idf=None):
        self.vocab = vocab or {}       # term -> index
        self.idf = idf or []           # index -> idf

    def fit(self, texts):
        docs = [set(tokenize(t)) for t in texts]
        df = Counter(term for d in docs for term in d)
        terms = sorted(df)
        self.vocab = {t: i for i, t in enumerate(terms)}
        n = len(docs)
        self.idf = [math.log((1 + n) / (1 + df[t])) + 1.0 for t in terms]
        return self

    def embed(self, text):
        counts = Counter(t for t in tokenize(text) if t in self.vocab)
        vec = {self.vocab[t]: (1.0 + math.log(c)) * self.idf[self.vocab[t]] for t, c in counts.items()}
        norm = math.sqrt(sum(w * w for w in vec.values()))
        return {i: w / norm for i, w in vec.items()} if norm else {}

    def term_weight(self, term):
        i = self.vocab.get(term)
        return self.idf[i] if i is not None else 0.0

    def to_dict(self):
        return {"vocab": self.vocab, "idf": self.idf}

    @classmethod
    def from_dict(cls, d):
        return cls(d["vocab"], d["idf"])
