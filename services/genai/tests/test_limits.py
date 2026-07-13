"""Unit tests for document input bounding."""

import os
from unittest.mock import patch

import pytest

from app.limits import bound_document, max_document_chars


def test_short_document_passes_through_untouched():
    with patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "1000"}):
        text, truncated = bound_document("a small document")

    assert text == "a small document"
    assert truncated is False


def test_document_at_the_cap_is_not_truncated():
    with patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "10"}):
        text, truncated = bound_document("0123456789")

    assert text == "0123456789"
    assert truncated is False


def test_oversized_document_is_truncated_at_a_word_boundary():
    with patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "20"}):
        text, truncated = bound_document("word " * 20)

    assert truncated is True
    assert len(text) <= 20
    assert not text.endswith(" ")


def test_truncation_without_a_nearby_space_cuts_hard_at_the_cap():
    with patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "10"}):
        text, truncated = bound_document("a" * 50)

    assert truncated is True
    assert len(text) == 10


def test_default_cap_applies_when_env_is_unset():
    with patch.dict(os.environ, {}, clear=False):
        os.environ.pop("MAX_DOCUMENT_CHARS", None)
        assert max_document_chars() == 120000


def test_invalid_cap_raises():
    with patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "not-a-number"}):
        with pytest.raises(RuntimeError, match="MAX_DOCUMENT_CHARS"):
            bound_document("text")
