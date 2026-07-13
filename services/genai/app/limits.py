"""Bounding document text before it reaches the model.

summarize/extract/tag feed the whole document into one prompt. Our target is
30-40 page reports, and a large one either overflows the context window or is
slow and expensive, so we cap the characters and truncate at a word boundary.
The endpoints return a ``truncated`` flag so the client can say the result
covers only the first part of the document.

Index/ask/search don't come through here: they bound their input by chunking and
only send a handful of chunks to the model.

  MAX_DOCUMENT_CHARS   character cap fed to a single prompt (default 120000)
"""

from app.env import int_env

# ~30k tokens, comfortably inside our default model's 128k-token context while
# still covering a dense 30-40 page report. Smaller-context models can lower it.
_DEFAULT_MAX_DOCUMENT_CHARS = 120000

# Only snap a truncation back to the previous space if that space is reasonably
# close to the cap; otherwise a document with no spaces near the end would lose
# far more than intended.
_WORD_BOUNDARY_FLOOR = 0.9


def max_document_chars() -> int:
    return int_env("MAX_DOCUMENT_CHARS", _DEFAULT_MAX_DOCUMENT_CHARS, minimum=1)


def bound_document(text: str) -> tuple[str, bool]:
    """Cap ``text`` at the configured character limit.

    Returns ``(text, truncated)``. When the text is within the cap it comes back
    unchanged with ``truncated=False``; otherwise it's cut near the cap at a word
    boundary and ``truncated=True``.
    """
    cap = max_document_chars()
    if len(text) <= cap:
        return text, False
    head = text[:cap]
    cut = head.rfind(" ")
    if cut > cap * _WORD_BOUNDARY_FLOOR:
        head = head[:cut]
    return head.rstrip(), True
