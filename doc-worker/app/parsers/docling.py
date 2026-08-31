import asyncio
import logging
from pathlib import Path

from concurrent.futures import ThreadPoolExecutor

from collections.abc import Generator
from collections import defaultdict


from docling.datamodel.base_models import InputFormat
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.datamodel.pipeline_options import (
    PdfPipelineOptions,
    EasyOcrOptions,
    TableStructureOptions,
)

from docling.backend.pypdfium2_backend import PyPdfiumDocumentBackend

from docling_core.types import DoclingDocument
from docling_core.types.doc.document import (
    GroupItem,
    SectionHeaderItem,
    TableItem,
    ListItem,
    TextItem,
    ContentLayer,
)
from docling_core.types.doc.labels import DocItemLabel

from docling.exceptions import ConversionError


from models.domain import ParsedBlock

from parsers.base import BaseParser

# from pipeline.tree_builder import DocumentTreeBuilder, DocumentNode
# from extracting.layout import DocumentHierarchyTracker

from extraction.transform import linearize_table
from utils.string import clean_string

_log = logging.getLogger(__name__)


class DoclingParser(BaseParser):

    def __init__(
        self, use_ocr: bool = False, ocr_lang: list[str] = None, batch_size: int = 1
    ):

        # default pdf pipeline for local parser
        self.ocr_lang = ocr_lang or ["vi"]

        pipeline_options = PdfPipelineOptions()
        pipeline_options.do_ocr = use_ocr
        pipeline_options.do_table_structure = True

        table_options = TableStructureOptions()
        table_options.do_cell_matching = True
        pipeline_options.table_structure_options = table_options

        if use_ocr:
            pipeline_options.ocr_options = EasyOcrOptions(lang=self.ocr_lang)

        pipeline_options.generate_parsed_pages = False
        pipeline_options.ocr_batch_size = batch_size
        pipeline_options.layout_batch_size = batch_size
        pipeline_options.table_batch_size = batch_size

        self.converter = DocumentConverter(
            format_options={
                InputFormat.PDF: PdfFormatOption(
                    pipeline_options=pipeline_options, backend=PyPdfiumDocumentBackend
                )
            }
        )

        self._pool = ThreadPoolExecutor(max_workers=batch_size * 2)

    async def parse(self, file_path: str | Path, **kwargs):
        _log.info(f"[Docling] Start local parsing: {file_path}")

        try:
            loop = asyncio.get_running_loop()

            conv_result = await loop.run_in_executor(
                self._pool, self.converter.convert, file_path
            )

            return conv_result.document
        except ConversionError as e:
            _log.error(f"Docling parser fail to convert document {file_path}: {e}")
            raise
        except Exception as e:
            _log.error(f"Fail to parse document {file_path}: {e}")
            raise


def _resolve_node(doc: DoclingDocument, str_node: str):
    path_components = str_node.split("/")

    if len(path_components) == 3:
        _, node, index = path_components
        node_doc = getattr(doc, node)

        obj = node_doc[int(index)]
    else:
        _, node = path_components
        obj = getattr(doc, node)

    return obj


def _format_header(text: str) -> str:
    if not isinstance(text, str):
        return ""

    cleaned_header = clean_string(text)
    column_parts = cleaned_header.split(".")

    clean_parts = [part.strip() for part in column_parts if part.strip()]

    return "_".join(clean_parts)


def _extract_text_data_from_node(
    doc: DoclingDocument, node
) -> Generator[tuple[str, int], None, None]:
    if hasattr(node, "text"):
        text = getattr(node, "text")

        prov = getattr(node, "prov", [])
        page_no = getattr(prov[0], "page_no", 1)
        yield text, page_no

    if hasattr(node, "children"):
        for child in getattr(node, "children", []):
            cref = getattr(child, "cref", "")
            sub_node = _resolve_node(doc, cref)

            yield from _extract_text_data_from_node(doc, sub_node)


def validate_header(doc: DoclingDocument) -> list[dict[str, any]]:
    header_candidates = []

    for child in getattr(doc.body, "children", []):
        cref = getattr(child, "cref", "")
        node = _resolve_node(doc, cref)

        if isinstance(node, SectionHeaderItem):
            header_text = getattr(node, "text", "")
            clean_header = clean_string(header_text.strip())
            header_candidates.append({"id": cref, "text": clean_header})

    return header_candidates


def validate_table_header(doc: DoclingDocument):
    table_header_candidates = []

    for child in getattr(doc.body, "children", []):
        cref = getattr(child, "cref", "")
        node = _resolve_node(doc, cref)

        if isinstance(node, TableItem):
            df = node.export_to_dataframe(doc)
            if df is not None and not df.empty:
                columns = df.columns.to_list()
                clean_columns = [_format_header(column) for column in columns if column]

                table_header_candidates.append(
                    {"id": cref, "text": "\t".join(clean_columns)}
                )

    return table_header_candidates


def extract_to_blocks(
    doc: DoclingDocument, page_map: dict, **kwargs
) -> list[ParsedBlock]:

    if doc is None or not doc.pages:
        return None

    valid_headers = kwargs.get("valid_headers", {})
    valid_table_headers = kwargs.get("valid_table_headers", {})

    blocks: list[ParsedBlock] = []

    for order, child in enumerate(getattr(doc.body, "children", [])):
        cref = getattr(child, "cref", "")

        node = _resolve_node(doc, cref)
        if not node:
            continue

        prov = getattr(node, "prov", [])
        page_no = getattr(prov[0], "page_no", 1) if prov else 1
        original_page = page_map.get(page_no, page_no)

        if isinstance(node, SectionHeaderItem):
            item_text = getattr(node, "text", "")
            cleaned_section = clean_string(item_text.strip())

            if valid_headers.get(cref, True):
                blocks.append(
                    ParsedBlock(
                        original_page=original_page,
                        page_order=order,
                        block_type="header",
                        content=cleaned_section,
                        metadata={},
                    )
                )
            else:
                blocks.append(
                    ParsedBlock(
                        original_page=original_page,
                        page_order=order,
                        block_type="text",
                        content=cleaned_section,
                        metadata={},
                    )
                )

        elif isinstance(node, (GroupItem, ListItem, TextItem)):
            if node.content_layer is ContentLayer.FURNITURE:
                if node.label in (
                    DocItemLabel.FOOTNOTE,
                    DocItemLabel.PAGE_FOOTER,
                ):
                    continue
                elif node.label is DocItemLabel.PAGE_HEADER:
                    page_header = getattr(node, "text", "")
                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=order,
                            block_type="text",
                            content=page_header,
                            metadata={},
                        )
                    )
                    continue

            for extracted_text, page_no_text in _extract_text_data_from_node(doc, node):
                if extracted_text:
                    original_page = page_map.get(page_no_text, page_no_text)
                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=order,
                            block_type="text",
                            content=extracted_text,
                        )
                    )
        elif isinstance(node, TableItem):
            df = node.export_to_dataframe(doc)

            if df is not None and not df.empty:
                columns = df.columns.to_list()
                clean_columns = [_format_header(column) for column in columns if column]

                linearize_rows = linearize_table(df)

                if valid_table_headers.get(cref, True):
                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=order,
                            block_type="table",
                            content=linearize_rows,
                            metadata={"headers": clean_columns},
                        )
                    )
                else:
                    flattened_text = []

                    flattened_text.append("\t\t".join(clean_columns))

                    for row in linearize_rows:
                        flattened_text.append("\t\t".join(row))

                    blocks.append(
                        ParsedBlock(
                            original_page=original_page,
                            page_order=order,
                            block_type="text",
                            content=flattened_text,
                        )
                    )

    return blocks
