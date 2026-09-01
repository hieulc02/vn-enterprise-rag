from functools import cached_property
from collections.abc import Callable, Generator

from langchain_text_splitters import RecursiveCharacterTextSplitter

from pipeline.tree_builder import DocumentNode, TableNode

from config.chunker import ChunkerConfig
from models.chunk import DocumentChunk, ChunkMetadata
from models.domain import ChunkBuffer


class SemanticChunker:

    def __init__(self, config: ChunkerConfig, token_function: Callable[[str], int]):
        self.token_limit = config.token_limit
        self.token_per_chunk = config.token_per_chunk
        self.token_function = token_function
        self.token_overlap = config.token_overlap

        self.fallback_splitter = RecursiveCharacterTextSplitter(
            chunk_size=self.token_per_chunk,
            chunk_overlap=self.token_overlap,
            length_function=self.token_function,
            separators=["\n\n", "\n", ". ", " ", ""],
        )

    @cached_property
    def _join_token(self) -> int:
        return self.token_function("\n")

    def _build_context_path(self, node: DocumentNode) -> str:
        path = []
        curr_node = node

        while curr_node:
            if curr_node.title:
                title_str = " ".join(curr_node.title).strip()
                if title_str and title_str not in {"Root", "table"}:
                    path.insert(0, title_str)
            curr_node = curr_node.parent

        if not path:
            return ""

        path_str = " > ".join(path)
        return f"[GLOBAL CONTEXT]\n{path_str}\n\n" if path_str else ""

    def chunk(self, node: DocumentNode, file_key: str) -> list[DocumentChunk]:

        chunks: list[DocumentChunk] = []
        buffer = ChunkBuffer()
        page: int = 1
        chunk_index: int = 0

        def flush_chunk(page: int):
            nonlocal chunk_index

            if buffer.is_empty():
                return

            chunk_index += 1

            chunks.append(
                DocumentChunk(
                    text=buffer.current_context + "\n".join(buffer.lines),
                    metadata=ChunkMetadata(document_id=file_key, page_number=page),
                    chunk_index=chunk_index,
                )
            )
            buffer.clear()

        for curr_node, text_item in self._traverse_tree(node):

            line_tokens = self.token_function(text_item) + self._join_token
            node_context = self._build_context_path(curr_node)
            page = curr_node.page

            if not buffer.is_empty() and buffer.current_context != node_context:
                flush_chunk(page)

            if buffer.is_empty():
                buffer.current_context = node_context
                buffer.context_token = (
                    self.token_function(node_context) if node_context else 0
                )
                buffer.tokens = buffer.context_token

            if buffer.tokens + line_tokens > self.token_per_chunk:
                flush_chunk(page)

                if line_tokens > self.token_per_chunk:

                    safe_chunk_size = max(
                        1, self.token_per_chunk - buffer.context_token
                    )
                    self.fallback_splitter._chunk_size = safe_chunk_size
                    sub_chunks = self.fallback_splitter.split_text(text_item)

                    for i, sc in enumerate(sub_chunks):
                        buffer.current_context = node_context
                        buffer.context_token = (
                            self.token_function(node_context) if node_context else 0
                        )
                        buffer.lines.append(sc)
                        buffer.tokens = buffer.context_token + self.token_function(sc)

                        if i < len(sub_chunks) - 1:
                            flush_chunk(page)

                    continue

            buffer.current_context = node_context
            buffer.lines.append(text_item)
            buffer.tokens += line_tokens

        flush_chunk(page)
        return chunks

    def _traverse_tree(
        self,
        current_node: DocumentNode,
    ) -> Generator[tuple[DocumentNode | None, str], None, None]:

        if isinstance(current_node, TableNode):
            yield from self._process_table_node(current_node)

        for content in current_node.content:
            yield current_node, content

        for child in current_node.children:
            yield from self._traverse_tree(child)

    def _process_table_node(self, node: TableNode):

        if not node.rows:
            return

        if node.headers:
            header_row = "| " + " | ".join(node.headers) + " |"
            yield node, header_row
            sep_row = "|" + "|".join(["---"] * len(node.headers)) + "|"
            yield node, sep_row

        for row in node.rows:
            row_pairs = [str(cell) if cell is not None else "" for cell in row]

            if not row_pairs:
                continue

            row_text = "| " + " | ".join(row_pairs) + " |"

            if self.token_function(row_text) > self.token_per_chunk:
                for col_idx, cell in enumerate(row_pairs):
                    header = (
                        node.headers[col_idx]
                        if col_idx < len(node.headers)
                        else f"Column {col_idx}"
                    )
                    yield node, f"{header}: {cell}"
            else:
                yield node, row_text
