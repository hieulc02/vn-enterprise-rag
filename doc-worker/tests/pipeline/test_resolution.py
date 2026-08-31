import pytest
from dataclasses import dataclass
from typing import Any

from app.pipeline.resolution import (
    merge_entity_properties,
    extract_table_to_entities,
    _build_row_description,
)
from app.models.extraction import TableRowDTO, TableExtractionDTO, TableCell

from tests.factories.chunk_factory import create_chunk_graph, create_table_context


@dataclass
class TestProperty:
    key: str
    value: Any


def dummy_string_normalize(text: str) -> str:
    return str(text).lower().strip()


def test_merge_entity_properties_add_new_str_value():
    target_dict = {"existing_key": "value_str"}
    new_props = [TestProperty(key="new_key", value="new_value")]

    is_dirty = merge_entity_properties(target_dict, new_props, dummy_string_normalize)

    assert is_dirty is True
    assert target_dict["new_key"] == "new_value"


def test_merge_entity_properties_skip_dupl_value():
    target_dict = {"existing_key": "dupl_str"}
    new_props = [TestProperty(key="new_key", value=" DUPL_STR ")]

    is_dirty = merge_entity_properties(target_dict, new_props, dummy_string_normalize)

    assert is_dirty is False
    assert "new_key" not in target_dict


def test_merge_entity_properties_with_same_dict_key():
    target_dict = {"same_key": "old_value"}
    new_props = [TestProperty(key="same_key", value="new_value")]

    is_dirty = merge_entity_properties(target_dict, new_props, dummy_string_normalize)

    assert is_dirty is True
    assert target_dict["same_key"] == "old_value | new_value"


def test_merge_entity_properties_with_non_str_value():
    target_dict = {"count": 1, "is_dirty": False}

    new_props = [
        TestProperty(key="count", value=2),
        TestProperty(key="is_dirty", value=True),
        TestProperty(key="new_key", value=0.1),
    ]

    is_dirty = merge_entity_properties(target_dict, new_props, dummy_string_normalize)

    assert is_dirty is True
    assert target_dict["count"] == 2
    assert target_dict["is_dirty"] == True
    assert target_dict["new_key"] == 0.1


def test_extract_table_entities_with_empty_line_items():

    table_extraction = TableExtractionDTO(
        table_context=create_table_context(primary_subject="financial_statement"),
        line_items=[],
    )

    chunk_graph = create_chunk_graph(
        chunk_id="empty_line_items",
        contains_narrative_text=False,
        contains_table=True,
        tabular_data=table_extraction,
    )

    entities, relationships = extract_table_to_entities(chunk_graph)

    assert len(entities) == 1
    assert len(relationships) == 0


def test_extract_table_entities_with_no_tabular_data():

    chunk_graph = create_chunk_graph(
        chunk_id="empty_line_items",
        contains_narrative_text=False,
        contains_table=True,
        tabular_data=None,
    )

    entities, relationships = extract_table_to_entities(chunk_graph)

    assert len(entities) == 0
    assert len(relationships) == 0


def test_extract_table_entities_parent_child_hierarchy():

    parent_row = TableRowDTO(
        row_concept="parent_row", row_type="FinancialMetric", code="100", cells=[]
    )
    numeric_cell = TableCell(
        column_header="numeric_header",
        header_type="TimePeriod",
        value="43.210",
        is_numeric=True,
    )
    non_numeric_cell = TableCell(
        column_header="non_numeric_header",
        header_type="BusinessSector",
        value="Test",
        is_numeric=False,
    )
    child_row = TableRowDTO(
        row_concept="child_row",
        row_type="TimePeriod",
        parent_row_concept="parent_row",
        code="110",
        cells=[numeric_cell, non_numeric_cell],
    )
    table_context = create_table_context(
        document_title="financial_statement",
        primary_subject="financial_statement_subject",
    )
    table_extraction = TableExtractionDTO(
        table_context=table_context,
        line_items=[parent_row, child_row],
    )

    chunk_graph = create_chunk_graph(
        chunk_id="parent_child_hier",
        contains_narrative_text=False,
        contains_table=True,
        tabular_data=table_extraction,
    )

    entities, relationships = extract_table_to_entities(chunk_graph)

    assert len(entities) == 5
    assert len(relationships) == 4

    table_entity = next(ent for ent in entities if ent.name == "financial_statement")
    parent_entity = next(
        ent
        for ent in entities
        if ent.description == _build_row_description(parent_row, table_context)
    )
    child_entity = next(
        ent
        for ent in entities
        if ent.description == _build_row_description(child_row, table_context)
    )
    numeric_cell_entity = next(
        ent
        for ent in entities
        if (ent.description is None and ent.name is None)
        and ent.labels[0] == "Observation"
    )
    non_numeric_cell_entity = next(
        ent
        for ent in entities
        if (ent.description is None and ent.name is None)
        and ent.labels[0] == "BusinessSector"
    )

    def get_relationship_from(source_id: str):
        return [r for r in relationships if r.target_local_id == source_id]

    table_relationship = get_relationship_from(table_entity.local_id)
    assert len(table_relationship) == 1

    assert table_relationship[0].type == "COMPONENT_OF"
    assert table_relationship[0].source_local_id == parent_entity.local_id

    parent_row_relationship = get_relationship_from(parent_entity.local_id)
    assert len(parent_row_relationship) == 1

    assert parent_row_relationship[0].type == "IS_CHILD_OF"
    assert parent_row_relationship[0].source_local_id == child_entity.local_id

    numeric_cell_relationship = get_relationship_from(numeric_cell_entity.local_id)
    assert len(numeric_cell_relationship) == 1

    assert numeric_cell_relationship[0].type == "HAS_OBSERVATION"
    assert numeric_cell_relationship[0].source_local_id == child_entity.local_id

    non_numeric_cell_relationship = get_relationship_from(
        non_numeric_cell_entity.local_id
    )
    assert len(non_numeric_cell_relationship) == 1

    assert non_numeric_cell_relationship[0].type == "RELATED_TO"
    assert non_numeric_cell_relationship[0].source_local_id == child_entity.local_id


@pytest.mark.parametrize(
    "custom_row_type,row_type,expected_prop_key",
    [
        (None, "TimePeriod", "TimePeriod"),
        ("CustomType", "Concept", "CustomType"),
    ],
)
def test_extract_table_entities_child_as_property(
    custom_row_type, row_type, expected_prop_key
):

    parent_row = TableRowDTO(
        row_concept="parent_row", row_type="FinancialMetric", code="100", cells=[]
    )
    child_row = TableRowDTO(
        row_concept="child_row",
        row_type=row_type,
        custom_row_type=custom_row_type,
        parent_row_concept="parent_row",
        code="110",
        cells=[],
    )
    table_context = create_table_context(
        document_title="financial_statement",
        primary_subject="financial_statement_subject",
    )
    table_extraction = TableExtractionDTO(
        table_context=table_context,
        line_items=[parent_row, child_row],
    )
    chunk_graph = create_chunk_graph(
        chunk_id="parent_child_hier",
        contains_narrative_text=False,
        contains_table=True,
        tabular_data=table_extraction,
    )

    entities, _ = extract_table_to_entities(chunk_graph)

    assert len(entities) == 2

    parent_entity = next(
        ent
        for ent in entities
        if ent.description == _build_row_description(parent_row, table_context)
    )

    new_prop = next(
        (prop for prop in parent_entity.properties if prop.key == expected_prop_key),
        None,
    )

    assert new_prop is not None
    assert new_prop.value == "child_row"


def test_extract_table_entities_missing_parent_concept():
    parent_row = TableRowDTO(
        row_concept="parent_row", row_type="FinancialMetric", code="100", cells=[]
    )
    child_row = TableRowDTO(
        row_concept="child_row",
        row_type="TimePeriod",
        parent_row_concept="non_exists_parent",
        code="110",
        cells=[],
    )
    table_context = create_table_context(
        document_title="financial_statement",
        primary_subject="financial_statement_subject",
    )
    table_extraction = TableExtractionDTO(
        table_context=table_context,
        line_items=[parent_row, child_row],
    )
    chunk_graph = create_chunk_graph(
        chunk_id="parent_child_hier",
        contains_narrative_text=False,
        contains_table=True,
        tabular_data=table_extraction,
    )

    entities, relationships = extract_table_to_entities(chunk_graph)

    assert len(entities) == 3
    assert len(relationships) == 1

    orphan_entity = next(
        ent
        for ent in entities
        if ent.description == _build_row_description(child_row, table_context)
    )

    orphan_relationships = [
        r for r in relationships if r.source_local_id == orphan_entity.local_id
    ]
    assert len(orphan_relationships) == 0
