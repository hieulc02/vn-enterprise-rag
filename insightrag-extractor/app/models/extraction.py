from pydantic import BaseModel, Field, model_validator
from typing import Literal

EntityType = Literal[
    "Organization",
    "Person",
    "Product",
    "Document",
    "BusinessSector",
    "FinancialMetric",
    "AssetLiability",
    "AccountingPolicy",
    "TimePeriod",
    "Concept",
]

RelationshipType = Literal[
    "OWNS",
    "INVESTS_IN",
    "COMPONENT_OF",
    "EXPLAINED_BY",
    "AFFECTED_BY",
    "REPORTED_BY",
    "HAS_OBSERVATION",
    "IS_CHILD_OF",
    "HAS_VALUE",
    "RELATED_TO",
]

TableArchitecture = Literal["FLAT_ENTITY", "DIMENSIONAL_MATRIX"]


class PropertyItem(BaseModel):
    key: str = Field(description="Standardized snake_case English key")
    original_label: str | None = Field(
        default=None,
        description="The exact Vietnamese label or concept as it physically appears in the text.",
    )
    value: str | bool | float | list[float] = Field(
        description="The exact extracted value, preserving original Vietnamese text."
    )


class LocalEntity(BaseModel):
    local_id: str = Field(description="A temporary ID for this chunk")
    name: str = Field(description="The name of the entity in Vietnamese")
    type: EntityType
    custom_type: str | None = Field(
        default=None,
        description="Optional custom sub-type. MUST be populated if type is 'Concept'.",
    )
    description: str | None = Field(
        default=None,
        description="A 1-2 sentence Vietnamese summary. RULE 1: If the entity is only part of a static footer, contact list, or header with no narrative action, you MUST return null. RULE 2: If there is narrative action, you MUST synthesize the [GLOBAL CONTEXT] and chunk text to explain what the entity is doing. DO NOT infer external knowledge.",
    )
    properties: list[PropertyItem] = Field(
        default_factory=list,
        description="Extract metadata here as key-value pairs.",
    )


class GraphEntity(LocalEntity):
    name: str | None = Field(default=None)
    labels: list[str] = Field(default_factory=list)
    is_tabular_data: bool = Field(default=False)

    def gen_fallback_description(self) -> str | None:
        if not self.name:
            return None

        props_string = (
            (
                f"{prop.original_label}: {prop.value}"
                if prop.original_label
                else prop.value
            )
            for prop in self.properties
        )
        return " | ".join([*props_string])


class LocalRelationship(BaseModel):
    source_local_id: str = Field(description="Must match a local_id from entities list")
    target_local_id: str = Field(description="Must match a local_id from entities list")
    type: RelationshipType
    description: str | None = Field(
        default=None,
        description="Briefly explain the edge in Vietnamese if using the 'RELATED_TO' fallback.",
    )
    properties: list[PropertyItem] = Field(
        default_factory=list,
        description="Extract edge metadata here as key-value pairs.",
    )


class TableContext(BaseModel):
    document_title: str | None = Field(
        default=None,
        description="The strict core root name of the document. Do not include parenthetical modifiers.",
    )
    context_modifier: str | None = Field(
        default=None,
        description="Parenthetical text physically adjacent to the title.",
    )
    section_heading: str | None = Field(
        default=None, description="The exact core text of the immediate heading."
    )
    reference_code: str | None = Field(
        default=None, description="Alphanumeric note indicators."
    )
    primary_subject: str = Field(
        description="REQUIRED: A concise 3-5 word summary of the table's core topic."
    )


class TableCell(BaseModel):
    column_header: str = Field(description="The exact column header.")
    header_type: EntityType = Field(
        description="Classify the semantic category of this column header"
    )
    custom_header_type: str | None = Field(
        default=None,
        description="MUST be populated with a 1-2 word descriptor if header_type is 'Concept'.",
    )

    value: str = Field(description="The exact string found in the cell.")
    is_numeric: bool = Field(
        description="True ONLY if the cell contains a numerical metric, financial value, or quantity. False if it contains text, categorical data, or names."
    )


class TableRowDTO(BaseModel):
    row_concept: str
    row_type: EntityType = Field(
        description="The semantic type of the entity represented in this row."
    )
    custom_row_type: str | None = Field(
        default=None,
        description="MUST be populated with a 1-2 word English descriptor if row_type is 'Concept'. Otherwise, leave null.",
    )
    parent_row_concept: str | None = Field(
        default=None,
        description="If this row is a sub-item indented under a category header, provide the exact name of that header row. If it is a top-level row, leave null.",
    )
    code: str | None = Field(default=None)
    note_reference: str | None = Field(default=None)
    cells: list[TableCell] = Field(default_factory=list)


class TableExtractionDTO(BaseModel):
    # table_architecture_strategy: TableArchitecture = Field(
    #     description="FLAT_ENTITY for standard lists. DIMENSIONAL_MATRIX for financials or sparse observation matrices."
    # )
    table_context: TableContext = Field(
        description="A structured breadcrumb capturing the exact structural location and semantic subject of the table."
    )
    line_items: list[TableRowDTO] = Field(default_factory=list)


class ChunkExtraction(BaseModel):
    """Send to LLM model for NER extraction"""

    contains_narrative_text: bool = Field(
        description="True if the chunk has standard text/paragraphs"
    )

    contains_table: bool = Field(
        description="True if the chunk contains a markdown/HTML table"
    )

    entities: list[LocalEntity] = Field(
        default_factory=list, description="List of entities extracted from the chunk"
    )
    relationships: list[LocalRelationship] = Field(
        default_factory=list,
        description="List of relationships extracted from the chunk",
    )

    tabular_data: TableExtractionDTO | None = Field(
        default=None, description="Populate ONLY if contains_table is True"
    )


class ChunkGraph(ChunkExtraction):
    """Bound programmatically in backend"""

    entities: list[GraphEntity] = Field(default_factory=list)
    chunk_id: str = Field(default="", description="Bound programmatically")
