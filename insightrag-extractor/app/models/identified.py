from pydantic import BaseModel


class Identified(BaseModel):
    """A protocol for an item with an ID"""

    id: str
    """The ID of the item"""

    short_id: str | None
    """Human readable ID"""
