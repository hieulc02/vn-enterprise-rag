import uuid
from pydantic import BaseModel, Field
from datetime import datetime, timezone


class Payload(BaseModel):
    file_key: str
    bucket_name: str
    extension: str


class Message(BaseModel):
    id: str
    aggregate_type: str
    event_type: str
    payload: Payload


class ResponseMessage(Message):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: str = Field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )
    correlation_id: str
