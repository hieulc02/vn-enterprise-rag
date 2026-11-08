from app.models.extraction import ChunkGraph, TableContext


def create_chunk_graph(
    chunk_id: str = "default_chunk_id",
    contains_narrative_text: bool = False,
    contains_table: bool = False,
    tabular_data=None,
    **kwargs
):

    return ChunkGraph(
        contains_narrative_text=contains_narrative_text,
        contains_table=contains_table,
        entities=kwargs.get("entities", []),
        relationships=kwargs.get("relationships", []),
        tabular_data=tabular_data,
        chunk_id=chunk_id,
    )


def create_table_context(primary_subject: str, **kwargs):

    return TableContext(
        document_title=kwargs.get("document_title", "default_title"),
        context_modifier=kwargs.get("context_modifier", "default_context_modifier"),
        section_heading=kwargs.get("section_heading", "default_section_heading"),
        reference_code=kwargs.get("reference_code", "default_reference_code"),
        primary_subject=primary_subject,
    )
