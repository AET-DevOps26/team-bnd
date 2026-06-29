"""Object storage access for retrieving document files by key.

Reads documents from the S3-compatible object storage (SeaweedFS) that the
Spring service uploads to. The connection is configured via the same S3_*
environment variables the Spring service uses, so both point at one bucket.

  S3_ENDPOINT    base URL of the S3 gateway (e.g. http://s3-storage:8333)
  S3_REGION      region label (SeaweedFS ignores it but boto3 requires one)
  S3_ACCESS_KEY  access key id
  S3_SECRET_KEY  secret access key
  S3_BUCKET      bucket documents are stored in
"""

import io
import os
from functools import lru_cache

import boto3
from botocore.config import Config
from pypdf import PdfReader

# SeaweedFS only serves bucket objects under path-style URLs
# (host/bucket/key), not the virtual-host style boto3 defaults to.
_PATH_STYLE = Config(s3={"addressing_style": "path"})

_PDF_MAGIC = b"%PDF"


class ObjectNotFoundError(Exception):
    """Raised when an object key does not exist in the bucket."""


class UnsupportedFileError(Exception):
    """Raised when an object cannot be decoded to text."""


@lru_cache(maxsize=1)
def _client():
    return boto3.client(
        "s3",
        endpoint_url=os.environ["S3_ENDPOINT"],
        region_name=os.getenv("S3_REGION", "eu-central-1"),
        aws_access_key_id=os.environ["S3_ACCESS_KEY"],
        aws_secret_access_key=os.environ["S3_SECRET_KEY"],
        config=_PATH_STYLE,
    )


def _bucket() -> str:
    return os.environ["S3_BUCKET"]


def _extract_text(data: bytes) -> str:
    """Turn raw object bytes into text.

    PDFs are detected by their magic header and parsed with pypdf; everything
    else is treated as UTF-8 text.
    """
    if data.startswith(_PDF_MAGIC):
        reader = PdfReader(io.BytesIO(data))
        pages = [page.extract_text() or "" for page in reader.pages]
        return "\n".join(pages).strip()

    try:
        return data.decode("utf-8").strip()
    except UnicodeDecodeError as e:
        raise UnsupportedFileError("object is neither a PDF nor valid UTF-8 text") from e


def fetch_text(object_key: str) -> str:
    """Download an object by key and return its text content.

    Raises:
        ObjectNotFoundError: the key does not exist in the bucket.
        UnsupportedFileError: the object cannot be decoded to text.
    """
    client = _client()
    try:
        response = client.get_object(Bucket=_bucket(), Key=object_key)
    except client.exceptions.NoSuchKey as e:
        raise ObjectNotFoundError(object_key) from e

    return _extract_text(response["Body"].read())
