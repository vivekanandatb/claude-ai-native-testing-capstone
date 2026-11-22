"""Retrieve top-k chunks with source IDs and similarity scores."""


def retrieve(index, question, k):
    query_vec = index.embedder.embed(question)
    results = index.search(query_vec, k)
    return [
        {"rank": r, "score": score, "chunk": chunk}
        for r, (score, chunk) in enumerate(results, start=1)
    ]
