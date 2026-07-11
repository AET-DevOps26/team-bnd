"""Entity extraction using LangChain structured output."""

from typing import Literal

from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.llm import get_llm, get_model_name

# Cap applied when the caller doesn't ask for a specific number. Long documents
# otherwise yield a sprawling list that's noisy to render and scan in the
# document view. Callers pass their own limit on the /genai/extract request.
DEFAULT_MAX_ENTITIES = 20


class _Entity(BaseModel):
    name: str = Field(description="The exact name or value of the entity as it appears in the text")
    type: Literal["PERSON", "DATE", "TOPIC", "ORGANIZATION"] = Field(
        description="Entity category: PERSON for named individuals, DATE for dates/time references, "
        "TOPIC for key subjects or themes, ORGANIZATION for companies/institutions/groups"
    )
    confidence: float = Field(ge=0.0, le=1.0, description="Confidence score between 0 and 1")


class _ExtractionResult(BaseModel):
    entities: list[_Entity] = Field(description="All identified entities from the text")


_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are a named-entity recognition system. Extract the most significant entities from the provided text. "
            "Identify PERSON (named individuals), DATE (dates, years, time expressions), "
            "TOPIC (key subjects, themes, concepts), and ORGANIZATION (companies, institutions, groups). "
            "Assign a confidence score based on how clearly the entity is identified in the text. "
            "Avoid duplicates. Return only entities that are genuinely present in the text. "
            "Return at most {max_entities} entities; when the text has more, keep the ones you are most confident about.",
        ),
        ("human", "{content}"),
    ]
)


def _select_top_entities(entities: list[_Entity], limit: int) -> list[_Entity]:
    """Keep the highest-confidence entities, up to the limit.

    The prompt already asks for a bounded list, but we enforce the cap here so a
    model that ignores the instruction can't flood the document view. The sort is
    stable, so entities sharing a confidence keep the model's original order.
    """
    ranked = sorted(entities, key=lambda e: e.confidence, reverse=True)
    return ranked[:limit]


def extract_entities(content: str, max_entities: int = DEFAULT_MAX_ENTITIES) -> tuple[list[dict], str]:
    """Extract named entities from document content.

    Returns at most ``max_entities`` entities, keeping the ones the model is most
    confident about.

    Returns:
        (entities, model_name) where entities is a list of dicts with name, type, confidence.
    """
    llm = get_llm()
    structured_llm = llm.with_structured_output(_ExtractionResult)
    chain = _PROMPT | structured_llm
    result: _ExtractionResult = chain.invoke({"content": content, "max_entities": max_entities})  # ty:ignore[invalid-assignment]
    entities = [e.model_dump() for e in _select_top_entities(result.entities, max_entities)]
    return entities, get_model_name()
