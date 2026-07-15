"""One-off re-index of every document that already has chunks in Weaviate.

Older builds pinned a deterministic Weaviate id per chunk, so re-indexing a
document deleted and re-inserted the same ids. A re-insert that landed on a
not-yet-cleaned tombstone could leave a chunk out of the searchable HNSW graph
while it still existed as an object, so retrieval silently missed part of the
document. Documents indexed under that scheme stay half-indexed until they're
rebuilt; run this once after deploying the fresh-id fix to heal them.

Usage (from a running genai container):

    docker compose exec genai python -m app.reindex
"""

import logging
from dataclasses import dataclass, field

from app.embeddings import chunk_text, embed_chunks
from app.storage import fetch_text
from app.vectorstore import index_chunks, list_object_keys

logger = logging.getLogger(__name__)


@dataclass
class ReindexReport:
    total: int = 0
    reindexed: int = 0
    failed: list[str] = field(default_factory=list)


def reindex_all() -> ReindexReport:
    """Rebuild the chunks of every currently-indexed document from object storage.

    One document failing (missing object, unreadable file, provider error) is
    logged and recorded, not fatal, so a single bad document can't abort the heal.
    """
    keys = list_object_keys()
    report = ReindexReport(total=len(keys))
    for key in keys:
        try:
            chunks = chunk_text(fetch_text(key), key)
            index_chunks(key, chunks, embed_chunks(chunks))
            report.reindexed += 1
            logger.info("reindexed %s (%d chunks)", key, len(chunks))
        except Exception:
            logger.exception("failed to reindex %s", key)
            report.failed.append(key)
    return report


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    report = reindex_all()
    logger.info("reindex complete: %d/%d succeeded, %d failed", report.reindexed, report.total, len(report.failed))
    if report.failed:
        logger.warning("failed keys: %s", report.failed)


if __name__ == "__main__":
    main()
