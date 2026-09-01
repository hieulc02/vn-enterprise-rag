from pydantic import BaseModel

from models.entity import Entity
from models.relationship import Relationship
from models.chunk import DocumentChunk


class DocumentGraph(BaseModel):
    document_id: str
    chunks: list[DocumentChunk]
    entities: list[Entity]
    relationships: list[Relationship]
