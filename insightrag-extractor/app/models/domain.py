from dataclasses import dataclass, field
from typing import Any, Literal


@dataclass(slots=True)
class ParsedBlock:
    original_page: int
    page_order: int
    block_type: Literal["header", "text", "table"]
    content: list[list[str]] | list[str] | str
    metadata: dict[str, Any] | None = None


@dataclass
class DocumentNode:
    level: int
    page: int
    title: list[str] = field(default_factory=list)
    content: list[str] = field(default_factory=list)
    children: list["DocumentNode"] = field(default_factory=list)
    parent: "DocumentNode | None" = field(default=None, repr=False)


@dataclass
class TableNode(DocumentNode):
    headers: list[str] = field(default_factory=list)
    rows: list[list[str]] = field(default_factory=list)


@dataclass
class ChunkBuffer:
    lines: list[str] = field(default_factory=list)
    tokens: int = 0
    current_context: str = ""
    context_token: int = 0

    def clear(self):
        self.lines.clear()
        self.tokens = 0
        self.current_context = ""

    def is_empty(self) -> bool:
        return len(self.lines) == 0
