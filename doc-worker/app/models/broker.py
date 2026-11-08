import uuid
from pydantic import BaseModel, Field
from datetime import datetime, timezone

from enum import StrEnum


class DocumentEventType(StrEnum):
    DOCUMENT_CREATED = "NEW"
    DOCUMENT_UPDATED = "UPDATED"
    DOCUMENT_DELETED = "DELETED"
    DOCUMENT_COMPLETED = "COMPLETED"


class Payload(BaseModel):
    file_key: str
    bucket_name: str
    content_type: str


class Message(BaseModel):
    id: str
    aggregate_type: str
    event_type: DocumentEventType
    payload: Payload


class ResponseMessage(Message):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: str = Field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )
    correlation_id: str
