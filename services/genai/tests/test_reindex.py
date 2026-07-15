"""Unit tests for the one-off re-index heal script.

Storage, embedding, and the vector store are mocked, so no network or running
services are needed.
"""

from unittest.mock import patch

import pytest

from app.embeddings import Chunk
from app.reindex import ReindexReport, main, reindex_all


def _chunks(key: str) -> list[Chunk]:
    return [Chunk(key, 0, "text")]


def test_reindex_all_rebuilds_every_indexed_document():
    with (
        patch("app.reindex.list_object_keys", return_value=["a", "b"]),
        patch("app.reindex.fetch_text", side_effect=lambda k: f"text of {k}"),
        patch("app.reindex.chunk_text", side_effect=lambda _text, key: _chunks(key)),
        patch("app.reindex.embed_chunks", return_value=[[0.1]]),
        patch("app.reindex.index_chunks") as mock_index,
    ):
        report = reindex_all()

    assert report.total == 2
    assert report.reindexed == 2
    assert report.failed == []
    assert [call.args[0] for call in mock_index.call_args_list] == ["a", "b"]


def test_reindex_all_records_failures_and_keeps_going():
    def fetch(key: str) -> str:
        if key == "bad":
            raise RuntimeError("object gone")
        return "text"

    with (
        patch("app.reindex.list_object_keys", return_value=["good", "bad", "also-good"]),
        patch("app.reindex.fetch_text", side_effect=fetch),
        patch("app.reindex.chunk_text", side_effect=lambda _text, key: _chunks(key)),
        patch("app.reindex.embed_chunks", return_value=[[0.1]]),
        patch("app.reindex.index_chunks"),
    ):
        report = reindex_all()

    assert report.total == 3
    assert report.reindexed == 2
    assert report.failed == ["bad"]


def test_reindex_all_with_nothing_indexed_is_a_noop():
    with (
        patch("app.reindex.list_object_keys", return_value=[]),
        patch("app.reindex.index_chunks", side_effect=AssertionError("must not index")),
    ):
        report = reindex_all()

    assert report.total == 0
    assert report.reindexed == 0
    assert report.failed == []


def test_main_exits_nonzero_when_any_document_failed():
    with patch("app.reindex.reindex_all", return_value=ReindexReport(total=2, reindexed=1, failed=["bad"])):
        with pytest.raises(SystemExit) as exc:
            main()

    assert exc.value.code == 1


def test_main_exits_zero_when_all_documents_succeed():
    with patch("app.reindex.reindex_all", return_value=ReindexReport(total=2, reindexed=2, failed=[])):
        main()  # must not raise SystemExit
