import pytest

from app.chunking.sematic import SemanticChunker
from app.config.chunker import ChunkerConfig
from app.models.domain import DocumentNode, TableNode


@pytest.fixture
def mock_token_function():
    def _count(text: str) -> int:
        count = len(text)
        # print(f"[DEBUG TOKENIZER] repr={repr(text)} -> length={count}")
        return count

    return _count


@pytest.fixture
def chunker(mock_token_function):
    config = ChunkerConfig(token_limit=1000, token_per_chunk=50, token_overlap=0)
    return SemanticChunker(
        config=config,
        token_function=mock_token_function,
    )


class TestSemanticChunker:

    def test_build_context_path_filters_root(self, chunker):
        section_title = "section 1"
        root = DocumentNode(level=0, page=0, title=["Root"])
        section = DocumentNode(level=0, page=1, title=[section_title], parent=root)
        sub_child = DocumentNode(level=0, page=1, title=["table"], parent=section)

        context = chunker._build_context_path(sub_child)

        assert context == f"[GLOBAL CONTEXT]\n{section_title}\n\n"

    def test_chunk_standard_accumulation(self, chunker):
        node = DocumentNode(
            level=1, page=1, content=["Content 1", "Content 2", "Content 3"]
        )

        chunks = chunker.chunk(node, "test_file")

        assert len(chunks) == 1
        assert "Content 1\nContent 2\nContent 3" in chunks[0].text
        assert chunks[0].chunk_index == 1

    def test_chunk_context_boundary_flush(self, chunker):
        root = DocumentNode(level=0, page=0, title=["Root"])
        section_a = DocumentNode(
            level=1, page=1, title=["Section A"], content=["Content A"], parent=root
        )
        section_b = DocumentNode(
            level=1, page=1, title=["Section B"], content=["Content B"], parent=root
        )

        root.children = [section_a, section_b]

        chunks = chunker.chunk(root, "test_file")

        assert len(chunks) == 2

        assert "Section A" in chunks[0].text
        assert "Content A" in chunks[0].text

        assert "Section B" in chunks[1].text
        assert "Content B" in chunks[1].text

    def test_chunk_fallback_for_large_lines(self, chunker):
        exceed_line = "A" * 51
        exceed_section = "Exceeded Section"  # 16 tokens
        global_context = f"[GLOBAL CONTEXT]\n{exceed_section}\n\n"  # 35 tokens

        # 50 - 35 = 15 so we left only 15 token per chuck for content --> it must split to 4 chunks

        node = DocumentNode(
            level=1, page=1, title=[exceed_section], content=[exceed_line]
        )
        chunks = chunker.chunk(node, "test_file")
        assert len(chunks) == 4

        assert global_context in chunks[0].text
        assert global_context in chunks[1].text
        assert global_context in chunks[2].text
        assert global_context in chunks[3].text

    def test_chunk_process_table_node(self, chunker):
        table_node = TableNode(
            level=1,
            page=1,
            headers=["Column 1", "Column 2"],
            rows=[["Row 1", "Row 2"], ["Row 3", "Row 4"]],
        )

        generator = chunker._process_table_node(table_node)
        items = [text for _, text in generator]

        assert items[0] == "| Column 1 | Column 2 |"
        assert items[1] == "|---|---|"
        assert items[2] == "| Row 1 | Row 2 |"
        assert items[3] == "| Row 3 | Row 4 |"

    def test_chunk_fallback_table_node_long_row(self, mock_token_function):
        config = ChunkerConfig(token_limit=100, token_per_chunk=40, token_overlap=0)
        strict_chunker = SemanticChunker(config, mock_token_function)

        table_node = TableNode(
            level=1,
            page=1,
            headers=["Column 1", "Column 2"],
            rows=[["LongRowExceedChunkLimit", "LongCellContent"]],
        )

        generator = strict_chunker._process_table_node(table_node)
        items = [text for _, text in generator]

        assert items[0] == "| Column 1 | Column 2 |"
        assert items[1] == "|---|---|"

        assert "Column 1: LongRowExceedChunkLimit" in items[2]
        assert "Column 2: LongCellContent" in items[3]

    def test_chunk_node_with_empty_content(self, chunker):
        node = DocumentNode(level=1, page=1, content=[])

        chunks = chunker.chunk(node, "empty_file")

        assert chunks == []
