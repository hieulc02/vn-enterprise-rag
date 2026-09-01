from collections import defaultdict


from models.domain import DocumentNode, TableNode, ParsedBlock
from extraction.layout import DocumentHierarchyTracker


class DocumentTreeBuilder:

    def __init__(self, tracker: DocumentHierarchyTracker | None = None):
        self.tracker = tracker
        self.root = DocumentNode(title=["Root"], level=0, page=0)
        self.curr_node = self.root
        self._page_buffer: defaultdict[int, list[str]] = defaultdict(list)

    def add_header(self, text: str, page: int = 1, **kwargs):

        level = kwargs.get("level", 0)

        if not level:
            level = self.tracker.get_level(text)

        if not self.curr_node.content:
            if page == self.curr_node.page and level == self.curr_node.level:
                self.curr_node.title.append(text)
                return

        if level == DocumentHierarchyTracker.UNKNOWN_LEVEL_FALLBACK:
            parent_node = self.root
        else:
            parent_node = self.curr_node

            while parent_node.parent and parent_node.level >= level:
                parent_node = parent_node.parent

        new_section = DocumentNode(
            title=[text], level=level, page=page, parent=parent_node
        )

        parent_node.children.append(new_section)
        self.curr_node = new_section

    def add_text(self, text: list[str] | str):
        if isinstance(text, list):
            self.curr_node.content.extend(text)
        elif isinstance(text, str):
            self.curr_node.content.append(text)

    def add_table_header(
        self, headers: list[str] | str, page: int, title: str = "table"
    ):
        # if not headers:
        #     return

        if isinstance(self.curr_node, TableNode) and self.curr_node.parent:
            self.curr_node = self.curr_node.parent

        if self.curr_node.level == DocumentHierarchyTracker.UNKNOWN_LEVEL_FALLBACK:
            level = 1
        else:
            level = self.curr_node.level + 1

        new_table_node = TableNode(
            title=[title], page=page, level=level, parent=self.curr_node
        )

        if isinstance(headers, list):
            new_table_node.headers.extend(headers)
        elif isinstance(headers, str):
            new_table_node.headers.append(headers)

        self.curr_node.children.append(new_table_node)
        self.curr_node = new_table_node

    def add_table_rows(self, rows: list[list[str] | list[str] | str]):

        if not isinstance(self.curr_node, TableNode):
            self.add_table_header(
                headers=[], title="implicit-table", page=self.curr_node.page
            )

        if isinstance(rows, str):
            self.curr_node.rows.append([rows])
        elif isinstance(rows, list):
            if len(rows) > 0 and isinstance(rows[0], list):
                self.curr_node.rows.extend(rows)
            else:
                self.curr_node.rows.append(rows)

    def buffer_page_text(self, page: int, text: str | list[str]):
        text_add = [text] if isinstance(text, str) else text
        curr_texts = self._page_buffer[page] + text_add
        self._page_buffer[page] = list(dict.fromkeys(curr_texts))

    def flush_page(self, page: int):
        if self._page_buffer[page]:
            self.add_text(self._page_buffer[page])
            self._page_buffer[page].clear()


def build_document_tree(parsed_blocks: list[ParsedBlock]):
    return _build_tree_from_parsed_block(
        builder=DocumentTreeBuilder(tracker=DocumentHierarchyTracker()),
        parsed_blocks=parsed_blocks,
    )


def _build_tree_from_parsed_block(
    builder: DocumentTreeBuilder, parsed_blocks: list[ParsedBlock]
):

    if not builder:
        builder = DocumentTreeBuilder(tracker=DocumentHierarchyTracker())

    last_processed_page = 1

    for block in parsed_blocks:
        last_processed_page = block.original_page

        if block.block_type == "header":
            builder.flush_page(builder.curr_node.page)
            metadata = block.metadata
            level = (
                metadata.get("level")
                if isinstance(metadata, dict)
                else getattr(metadata, "level")
            )
            builder.add_header(
                text=block.content, page=block.original_page, level=level
            )
            builder.flush_page(block.original_page)
        elif block.block_type == "text":
            if block.original_page == builder.curr_node.page:
                builder.add_text(text=block.content)
            else:
                builder.buffer_page_text(block.original_page, block.content)
        elif block.block_type == "table":
            metadata = block.metadata
            headers = (
                metadata.get("headers")
                if isinstance(metadata, dict)
                else getattr(metadata, "headers", [])
            )
            # if not headers:
            #     continue

            builder.flush_page(builder.curr_node.page)
            builder.add_table_header(headers=headers, page=block.original_page)
            builder.flush_page(block.original_page)
            builder.add_table_rows(block.content)

    builder.flush_page(last_processed_page)

    return builder.root
