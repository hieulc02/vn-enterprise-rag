import pytest
from app.models.domain import ParsedBlock
from app.pipeline.tree_builder import build_document_tree, TableNode


class TestDocumentTreeBuilderIntegration:

    def test_build_tree_from_parsed_block(self):

        blocks = [
            ParsedBlock(
                original_page=1,
                page_order=1,
                block_type="header",
                content="H1: Intro",
                metadata={"level": 1},
            ),
            ParsedBlock(
                original_page=1, page_order=2, block_type="text", content="text 1"
            ),
            ParsedBlock(
                original_page=1,
                page_order=3,
                block_type="table",
                content=[["row 1"]],
                metadata={"headers": ["Column 1"]},
            ),
            ParsedBlock(
                original_page=2, page_order=1, block_type="text", content="text 2"
            ),
            ParsedBlock(
                original_page=2,
                page_order=2,
                block_type="header",
                content="H2: Section",
                metadata={"level": 2},
            ),
        ]

        root = build_document_tree(blocks)

        assert len(root.children) == 1

        h1_node = root.children[0]
        assert h1_node.title == ["H1: Intro"]
        assert "text 1" in h1_node.content
        assert len(h1_node.children) == 2

        table_node = h1_node.children[0]
        assert isinstance(table_node, TableNode)
        assert table_node.headers == ["Column 1"]
        assert table_node.rows == [["row 1"]]

        h2_node = h1_node.children[1]
        assert h2_node.title == ["H2: Section"]
        assert "text 2" in h2_node.content
