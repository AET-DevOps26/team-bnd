"""Content-based tagging using LangChain structured output."""

import re

from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.llm import get_llm, get_model_name

# Keep the number of tags per document small so the knowledge base stays
# browsable instead of drowning in one-off labels.
_MAX_TAGS = 5

# Tags are short labels, not sentences.
_MAX_TAG_LENGTH = 25

# lowercase ascii words separated by a single space or hyphen; anything else
# is rejected, which also blocks prompt injection via knownTags.
_TAG_PATTERN = re.compile(r"^[a-z0-9]+(?:[ -][a-z0-9]+)*$")


def _is_valid_tag(tag: str) -> bool:
    return bool(_TAG_PATTERN.match(tag))


class _TaggingResult(BaseModel):
    tags: list[str] = Field(description=f"Between 0 and {_MAX_TAGS} short topical tags describing the document's subject matter")


_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You assign topical tags that describe what a document is about, so it can be categorised "
            "and filtered in a knowledge base. "
            f"Return at most {_MAX_TAGS} tags, fewer when the document is narrow. "
            "Each tag is one or two lowercase words naming a broad subject, field, or theme "
            "(for example 'machine learning', 'finance', 'climate policy'), not a specific name, date, or quote. "
            "Prefer common, reusable terms over document-specific wording so the same topic gets the same tag "
            "across documents. No duplicates, no hashtags, no punctuation. "
            "{reuse_instructions}",
        ),
        ("human", "{content}"),
    ]
)


def _normalize(tags: list[str]) -> list[str]:
    """Lowercase and trim tags, drop blanks and duplicates, and cap the count.

    The prompt already asks for a bounded, clean list, but we enforce it here so a
    misbehaving model can't push 30 one-off tags into the knowledge base.
    """
    seen: set[str] = set()
    result: list[str] = []
    for tag in tags:
        cleaned = tag.strip().lower()
        if cleaned and len(cleaned) <= _MAX_TAG_LENGTH and cleaned not in seen and _is_valid_tag(cleaned):
            seen.add(cleaned)
            result.append(cleaned)
    return result[:_MAX_TAGS]


def generate_tags(content: str, known_tags: list[str] | None = None) -> tuple[list[str], str]:
    """Generate content-based topical tags for a document.

    When known_tags is given, the prompt is biased toward reusing those labels so the
    vocabulary doesn't fragment into one-off tags across documents.

    Returns:
        (tags, model_name) where tags is a bounded, de-duplicated list of lowercase labels.
    """
    llm = get_llm()
    structured_llm = llm.with_structured_output(_TaggingResult)
    chain = _PROMPT | structured_llm
    reuse = ""
    if known_tags:
        # Lowercase + allowlist also blocks prompt injection: knownTags are
        # interpolated into the system prompt, so reject anything that isn't a
        # tag-shaped lowercase ascii string.
        safe_tags = [t.strip().lower() for t in known_tags if len(t) <= _MAX_TAG_LENGTH]
        safe_tags = [t for t in safe_tags if _is_valid_tag(t)]
        sample = ", ".join(safe_tags[:50])
        if sample:
            reuse = f"These tags already exist in the knowledge base: {sample}. Prefer reusing one of them when it fits the document."
    result: _TaggingResult = chain.invoke({"content": content, "reuse_instructions": reuse})  # ty:ignore[invalid-assignment]
    return _normalize(result.tags), get_model_name()
