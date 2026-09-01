from pydantic import BaseModel, Field


class EntitiesAdjudicator(BaseModel):
    analysis: str = Field(
        description="Step-by-step comparison of the two entities, checking names, context, and applying the matching rules."
    )
    is_same_entity: bool = Field(
        description="True if they refer to the EXACT same entity, False otherwise."
    )
