import asyncio

from parsers.docling import extract_to_blocks, validate_header, validate_table_header
from prompts.validation import HEADER_VALIDATION_PROMPT, TABLE_VALIDATION_PROMPT
from pipeline.validation import DocumentValidation
from models.domain import ParsedBlock


def docling_handler(validator: DocumentValidation):

    async def handling_docling(doc, page_map: dict[int, int]) -> list[ParsedBlock]:
        header_candidates = validate_header(doc)
        table_header_candidates = validate_table_header(doc)

        valid_headers, valid_table_headers = await asyncio.gather(
            validator.validate_all(HEADER_VALIDATION_PROMPT, header_candidates),
            validator.validate_all(TABLE_VALIDATION_PROMPT, table_header_candidates),
        )

        return extract_to_blocks(
            doc=doc,
            page_map=page_map,
            valid_headers=valid_headers,
            valid_table_headers=valid_table_headers,
        )

    return handling_docling
