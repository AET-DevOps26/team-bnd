"""Unit tests for the object storage module.

The S3 client is mocked, so these tests need no network or credentials. PDF
parsing runs for real against a small in-memory PDF built with pypdf.
"""

from unittest.mock import MagicMock, patch

import pytest


def _fake_s3_with_object(data: bytes):
    """Build a mock boto3 S3 client whose get_object returns the given bytes."""
    body = MagicMock()
    body.read.return_value = data
    client = MagicMock()
    client.get_object.return_value = {"Body": body}
    return client


def _build_pdf(text: str) -> bytes:
    """Build a minimal valid single-page PDF whose page shows the given text.

    A real Type1 Helvetica font resource is referenced so pypdf can decode the
    Tj operator back to text in extract_text.
    """
    content = f"BT /F1 12 Tf 10 100 Td ({text}) Tj ET"
    return (
        b"%PDF-1.4\n"
        b"1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
        b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
        b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]"
        b"/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>endobj\n"
        b"4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n"
        b"5 0 obj<</Length " + str(len(content)).encode() + b">>stream\n" + content.encode() + b"\nendstream endobj\n"
        b"xref\n0 6\ntrailer<</Root 1 0 R/Size 6>>\nstartxref\n0\n%%EOF"
    )


def _clear_client_cache():
    from app import storage

    storage._client.cache_clear()


@pytest.fixture(autouse=True)
def _s3_env(monkeypatch):
    """Provide dummy S3 settings so the client builder does not fail on missing env."""
    monkeypatch.setenv("S3_ENDPOINT", "http://s3-storage:8333")
    monkeypatch.setenv("S3_ACCESS_KEY", "admin")
    monkeypatch.setenv("S3_SECRET_KEY", "secret")
    monkeypatch.setenv("S3_BUCKET", "alexandria-storage")


def test_fetch_text_decodes_utf8_object():
    client = _fake_s3_with_object(b"Hello from a text file.")
    _clear_client_cache()

    with patch("app.storage.boto3.client", return_value=client):
        from app.storage import fetch_text

        assert fetch_text("uploads/abc/note.txt") == "Hello from a text file."


def test_fetch_text_parses_pdf_object():
    pdf_bytes = _build_pdf("Invoice total 1234")
    client = _fake_s3_with_object(pdf_bytes)
    _clear_client_cache()

    with patch("app.storage.boto3.client", return_value=client):
        from app.storage import fetch_text

        result = fetch_text("uploads/abc/invoice.pdf")

    assert "1234" in result


def test_fetch_text_raises_for_missing_object():
    from app.storage import ObjectNotFoundError

    client = MagicMock()
    client.exceptions.NoSuchKey = type("NoSuchKey", (Exception,), {})
    client.get_object.side_effect = client.exceptions.NoSuchKey()
    _clear_client_cache()

    with patch("app.storage.boto3.client", return_value=client):
        from app.storage import fetch_text

        with pytest.raises(ObjectNotFoundError):
            fetch_text("does/not/exist")


def test_fetch_text_raises_for_undecodable_bytes():
    from app.storage import UnsupportedFileError

    client = _fake_s3_with_object(b"\xff\xfe\x00\x01 binary junk \x80")
    _clear_client_cache()

    with patch("app.storage.boto3.client", return_value=client):
        from app.storage import fetch_text

        with pytest.raises(UnsupportedFileError):
            fetch_text("uploads/abc/image.bin")
