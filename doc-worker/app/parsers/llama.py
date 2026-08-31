# from collections import defaultdict
import logging

from llama_cloud import AsyncLlamaCloud

from llama_cloud.types.parsing_get_response import (
    Items,
    ItemsPageStructuredResultPage,
    ItemsPageFailedStructuredPage,
)

from llama_cloud.types.heading_item import HeadingItem
from llama_cloud.types.text_item import TextItem
from llama_cloud.types.table_item import TableItem

from models.domain import ParsedBlock

from prompts.llama import LLAMA_VAS_PROMPT
from parsers.base import BaseParser

_log = logging.getLogger(__name__)


class LLamaParser(BaseParser):
    def __init__(self, api_key: str = None):
        self.client = AsyncLlamaCloud(api_key=api_key)

    async def parse(self, file_path: str, **kwargs):
        _log.info(f"[LLama] Start cloud parsing: {file_path}")

        output_format = kwargs.get("output_format", "items").lower()

        file = await self.client.files.create(file=file_path, purpose="parse")

        result = await self.client.parsing.parse(
            file_id=file.id,
            tier="agentic",
            version="latest",
            agentic_options={"custom_prompt": LLAMA_VAS_PROMPT},
            verbose=True,
            processing_options={
                # "cost_optimizer": {"enable": True},
                "ocr_parameters": {"languages": ["vi"]},
            },
            disable_cache=False,
            expand=["markdown", "items"],
        )

        return result.markdown if output_format == "markdown" else result.items


def _traverse_page_items(items):
    for item in items:
        if hasattr(item, "items") and item.items:
            yield from _traverse_page_items(item.items)
        else:
            yield item


def extract_to_blocks(doc: Items, page_map: dict[int, int]) -> list[ParsedBlock]:

    if doc is None or not doc.pages:
        return None

    blocks: list[ParsedBlock] = []

    for page in doc.pages:
        if isinstance(page, ItemsPageStructuredResultPage):
            page_no = page.page_number
            original_page = page_map.get(page_no, page_no)

            for index, item in enumerate(_traverse_page_items(page.items)):

                if isinstance(item, HeadingItem):

                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=index,
                            block_type="header",
                            content=item.value,
                            metadata={"level": item.level} if item.level else {},
                        )
                    )

                elif isinstance(item, TextItem):

                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=index,
                            block_type="text",
                            content=item.value,
                            metadata={},
                        )
                    )

                elif isinstance(item, TableItem):

                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=index,
                            block_type="table",
                            content=item.rows[1:],
                            metadata={"headers": item.rows[0]} if item.rows else {},
                        )
                    )

        elif isinstance(page, ItemsPageFailedStructuredPage):
            raise ValueError(f"Failed to extract page {page.page_number}: {page.error}")

    return blocks
