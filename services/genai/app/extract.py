"""Entity extraction using LangChain structured output."""

from typing import Literal

from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.llm import get_llm, get_model_name


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
            "You are a named-entity recognition system. Extract all significant entities from the provided text. "
            "Identify PERSON (named individuals), DATE (dates, years, time expressions), "
            "TOPIC (key subjects, themes, concepts), and ORGANIZATION (companies, institutions, groups). "
            "Assign a confidence score based on how clearly the entity is identified in the text. "
            "Avoid duplicates. Return only entities that are genuinely present in the text.",
        ),
        ("human", "{content}"),
    ]
)


def extract_entities(content: str) -> tuple[list[dict], str]:
    """Extract named entities from document content.

    Returns:
        (entities, model_name) where entities is a list of dicts with name, type, confidence.
    """
    llm = get_llm()
    structured_llm = llm.with_structured_output(_ExtractionResult)
    chain = _PROMPT | structured_llm
    result: _ExtractionResult = chain.invoke({"content": content})  # ty:ignore[invalid-assignment]
    entities = [e.model_dump() for e in result.entities]
    return entities, get_model_name()
