import pytest

from app.pipeline.tree_builder import (
    DocumentTreeBuilder,
    TableNode,
    build_document_tree,
)
from app.extraction.layout import DocumentHierarchyTracker


@pytest.fixture
def builder():
    tracker = DocumentHierarchyTracker()
    return DocumentTreeBuilder(tracker)


class TestDocumentTreeBuilder:

    def test_add_header_hierarchy_nesting(self, builder):
        builder.add_header("I. Header 1", level=1)
        h1_node = builder.curr_node

        builder.add_header("A. Sub-header 1", level=2)
        h2_node = builder.curr_node

        builder.add_header("1. Detail 1", level=3)
        h3_node = builder.curr_node

        builder.add_header("B. Sub-header 2", level=2)
        h1_sibling_node = builder.curr_node

        assert h2_node.parent == h1_node
        assert h3_node.parent == h2_node
        assert h1_sibling_node.parent == h1_node
        assert len(h1_node.children) == 2

    def test_add_header_concatnates_same_level_content(self, builder):
        builder.add_header("Section 1", page=1, level=1)
        builder.add_header("Section 2", page=1, level=1)

        assert len(builder.root.children) == 1
        assert builder.curr_node.title == ["Section 1", "Section 2"]

    def test_add_text(self, builder):
        builder.add_header("Section", page=1, level=1)
        builder.add_text("Detail")
        builder.add_text(["Paragraph 1", "Paragraph 2"])

        assert builder.curr_node.content == ["Detail", "Paragraph 1", "Paragraph 2"]

    def test_add_table_header_creates_table_node(self, builder):
        builder.add_header("Section", page=1, level=1)

        builder.add_table_header(headers=["Column 1", "Column 2"], page=1)

        assert isinstance(builder.curr_node, TableNode)
        assert builder.curr_node.level == 2
        assert builder.curr_node.headers == ["Column 1", "Column 2"]

        builder.add_table_rows(rows=[["Row 1", "Row 2"]])
        assert builder.curr_node.rows == [["Row 1", "Row 2"]]

    def test_add_table_adjacent_tables(self, builder):
        builder.add_table_header(headers=["Column 1", "Column 2"], page=1)
        first_table = builder.curr_node
        builder.add_table_header(headers=["Column 3", "Column 4"], page=2)
        adjacent_table = builder.curr_node

        assert adjacent_table.parent == first_table.parent
        assert adjacent_table.level == first_table.level
        assert len(builder.root.children) == 2

    def test_add_table_rows_fallback_without_table_node(self, builder):
        builder.add_header("H1", page=1, level=1)
        section_node = builder.curr_node
        builder.add_table_rows(rows=[["Row 1", "Row 2"]])

        assert builder.curr_node.rows == [["Row 1", "Row 2"]]
        assert isinstance(builder.curr_node, TableNode)
        assert builder.curr_node.parent == section_node

    def test_page_buffer_deduplication_and_flush(self, builder):
        builder.add_header("H1", page=1, level=1)

        builder.buffer_page_text(page=2, text="text_dupl")
        builder.buffer_page_text(page=2, text="text_uniq")
        builder.buffer_page_text(page=2, text="text_dupl")

        builder.flush_page(2)

        assert builder.curr_node.content == ["text_dupl", "text_uniq"]
        assert builder._page_buffer[2] == []


from app.models.domain import ParsedBlock


def test_build_document_tree_simple_parsed_block():
    blocks = [
        {
            "original_page": 1,
            "page_order": 1,
            "block_type": "header",
            "content": "H1",
            "metadata": {"level": 1},
        },
        {
            "original_page": 2,
            "page_order": 2,
            "block_type": "text",
            "content": "text_1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 3,
            "block_type": "header",
            "content": "H2",
            "metadata": {"level": 1},
        },
        {
            "original_page": 2,
            "page_order": 4,
            "block_type": "text",
            "content": "text_2",
            "metadata": {},
        },
    ]

    parsed_blocks = [ParsedBlock(**block) for block in blocks]

    root_node = build_document_tree(parsed_blocks)

    assert root_node.children[0].title == ["H1"]
    assert root_node.children[0].content == ["text_1"]
    assert root_node.children[0].level == 1
    assert root_node.children[1].title == ["H2"]
    assert root_node.children[1].content == ["text_2"]
    assert root_node.children[1].level == 1


def test_build_document_tree_hierarchy_parsed_block():
    blocks = [
        {
            "original_page": 1,
            "page_order": 1,
            "block_type": "header",
            "content": "H1",
            "metadata": {"level": 1},
        },
        {
            "original_page": 2,
            "page_order": 2,
            "block_type": "header",
            "content": "H1.1",
            "metadata": {"level": 2},
        },
        {
            "original_page": 2,
            "page_order": 3,
            "block_type": "text",
            "content": "text_1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 4,
            "block_type": "header",
            "content": "H2.1",
            "metadata": {"level": 2},
        },
    ]

    parsed_blocks = [ParsedBlock(**block) for block in blocks]

    root_node = build_document_tree(parsed_blocks)

    assert root_node.children[0].title == ["H1"]
    assert root_node.children[0].level == 1
    assert root_node.children[0].children[0].title == ["H1.1"]
    assert root_node.children[0].children[0].level == 2
    assert root_node.children[0].children[0].content == ["text_1"]
    assert root_node.children[0].children[1].title == ["H2.1"]
    assert root_node.children[0].children[1].level == 2


def test_build_document_tree_parsed_blocks_with_table():
    blocks = [
        {
            "original_page": 1,
            "page_order": 1,
            "block_type": "header",
            "content": "H1",
            "metadata": {"level": 1},
        },
        {
            "original_page": 1,
            "page_order": 2,
            "block_type": "text",
            "content": "text_1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 3,
            "block_type": "text",
            "content": "text_2",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 4,
            "block_type": "table",
            "content": [["row1", "val1"], ["row2", "val2"]],
            "metadata": {"headers": ["col1", "col2"]},
        },
    ]

    parsed_blocks = [ParsedBlock(**block) for block in blocks]

    root_node = build_document_tree(parsed_blocks)

    assert root_node.children[0].title == ["H1"]
    assert root_node.children[0].level == 1
    assert root_node.children[0].content == ["text_1", "text_2"]
    assert isinstance(root_node.children[0].children[0], TableNode)
    assert root_node.children[0].children[0].headers == ["col1", "col2"]
    assert root_node.children[0].children[0].rows == [
        ["row1", "val1"],
        ["row2", "val2"],
    ]


def test_build_document_tree_parsed_blocks_with_multiple_tables():
    blocks = [
        {
            "original_page": 1,
            "page_order": 1,
            "block_type": "header",
            "content": "H1",
            "metadata": {"level": 1},
        },
        {
            "original_page": 1,
            "page_order": 2,
            "block_type": "text",
            "content": "text_1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 3,
            "block_type": "table",
            "content": [["row1", "val1"], ["row2", "val2"]],
            "metadata": {"headers": ["col1", "col2"]},
        },
        {
            "original_page": 3,
            "page_order": 4,
            "block_type": "table",
            "content": [["row3", "val3"], ["row4", "val4"]],
            "metadata": {"headers": ["col3", "col4"]},
        },
    ]

    parsed_blocks = [ParsedBlock(**block) for block in blocks]

    root_node = build_document_tree(parsed_blocks)

    assert root_node.children[0].title == ["H1"]
    assert root_node.children[0].level == 1
    assert root_node.children[0].content == ["text_1"]
    assert isinstance(root_node.children[0].children[0], TableNode)
    assert root_node.children[0].children[0].headers == ["col1", "col2"]
    assert root_node.children[0].children[0].rows == [
        ["row1", "val1"],
        ["row2", "val2"],
    ]
    assert isinstance(root_node.children[0].children[1], TableNode)
    assert root_node.children[0].children[1].headers == ["col3", "col4"]
    assert root_node.children[0].children[1].rows == [
        ["row3", "val3"],
        ["row4", "val4"],
    ]


def test_build_document_tree_hierarchy_parsed_blocks_without_level():
    blocks = [
        {
            "original_page": 1,
            "page_order": 1,
            "block_type": "header",
            "content": "H1",
            "metadata": {},
        },
        {
            "original_page": 1,
            "page_order": 2,
            "block_type": "text",
            "content": "text_1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 3,
            "block_type": "header",
            "content": "1.1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 4,
            "block_type": "text",
            "content": "text_2",
            "metadata": {},
        },
        {
            "original_page": 3,
            "page_order": 5,
            "block_type": "table",
            "content": [["row1", "val1"], ["row2", "val2"]],
            "metadata": {"headers": ["col1", "col2"]},
        },
    ]

    parsed_blocks = [ParsedBlock(**block) for block in blocks]

    root_node = build_document_tree(parsed_blocks)

    assert root_node.children[0].title == ["H1"]
    assert root_node.children[0].level == 1
    assert root_node.children[0].content == ["text_1"]
    assert root_node.children[0].children[0].title == ["1.1"]
    assert root_node.children[0].children[0].level == 2
    assert root_node.children[0].children[0].content == ["text_2"]
    assert isinstance(root_node.children[0].children[0].children[0], TableNode)
    assert root_node.children[0].children[0].children[0].headers == ["col1", "col2"]
    assert root_node.children[0].children[0].children[0].rows == [
        ["row1", "val1"],
        ["row2", "val2"],
    ]


def test_build_document_tree_hierarchy_page_buffer():
    blocks = [
        {
            "original_page": 1,
            "page_order": 1,
            "block_type": "header",
            "content": "H1",
            "metadata": {},
        },
        {
            "original_page": 1,
            "page_order": 2,
            "block_type": "text",
            "content": "text_1",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 3,
            "block_type": "text",
            "content": "text_2",
            "metadata": {},
        },
        {
            "original_page": 3,
            "page_order": 3,
            "block_type": "text",
            "content": "text_3",
            "metadata": {},
        },
        {
            "original_page": 2,
            "page_order": 4,
            "block_type": "header",
            "content": "H2",
            "metadata": {},
        },
    ]

    parsed_blocks = [ParsedBlock(**block) for block in blocks]

    root_node = build_document_tree(parsed_blocks)

    assert root_node.children[0].title == ["H1"]
    assert root_node.children[0].level == 1
    assert root_node.children[0].content == ["text_1", "text_2", "text_3"]
    assert root_node.children[1].title == ["H2"]
    assert root_node.children[1].level == 1
