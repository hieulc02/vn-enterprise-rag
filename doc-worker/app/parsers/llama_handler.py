from parsers.llama import extract_to_blocks

from models.domain import ParsedBlock


def llama_handler(**kwargs):

    async def handling_llama(doc, page_map: dict[int, int]) -> list[ParsedBlock]:
        return extract_to_blocks(doc, page_map)

    return handling_llama
