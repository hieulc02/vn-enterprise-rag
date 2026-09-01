import hashlib
from pydantic import BaseModel, Field, model_validator


class ChunkMetadata(BaseModel):
    document_id: str
    page_number: int


class DocumentChunk(BaseModel):
    chunk_id: str = Field(default="", description="Auto-generated SHA-256 hash")
    text: str = Field(description="The raw text/markdown of the chunk")
    metadata: ChunkMetadata
    chunk_index: int = Field(default=0, description="Sequential position in document")
    embedding: list[float] | None = Field(
        default=None, description="Vector embedding of chunk text"
    )

    @model_validator(mode="after")
    def generate_chunk_id(self) -> "DocumentChunk":
        if not self.chunk_id:
            unique_string = f"{self.metadata.document_id}_{self.metadata.page_number}_{self.chunk_index}_{self.text}"
            self.chunk_id = hashlib.sha256(unique_string.encode("utf-8")).hexdigest()
        return self
