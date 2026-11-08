from pydantic import Field
from typing import Any

from models.named import Named


class Entity(Named):
    aliases: set[str] = Field(default_factory=set)
    labels: list[str] = Field(default_factory=list)
    description: str | None = Field(default=None)
    # sematic_embedding: list[float] | None = Field(default=None)
    source_chunk_ids: set[str] = Field(default_factory=set)
    properties: dict[str, Any] = Field(default_factory=dict)
