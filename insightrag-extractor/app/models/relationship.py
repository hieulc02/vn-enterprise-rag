from pydantic import Field
from typing import Any

from models.identified import Identified


class Relationship(Identified):
    source: str
    """The ID of the source Entity"""
    target: str
    """The ID of the target Entity"""
    type: str
    description: str | None = Field(default=None)
    weight: float = Field(default=1.0)
    source_chunk_ids: set[str] = Field(default_factory=set)
    properties: dict[str, Any] = Field(default_factory=dict)
