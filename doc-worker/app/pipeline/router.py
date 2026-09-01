import logging
import pymupdf as fitz

logger = logging.getLogger(__name__)


def document_page_classify(
    file_path: str, text_threshold: int = 100
) -> dict[str, tuple[fitz.Document, dict[int, int]]]:
    doc = fitz.open(file_path)

    digital_doc, scanned_doc = fitz.open(), fitz.open()
    digital_map, scanned_map = {}, {}

    try:
        with fitz.open(file_path) as doc:
            for page_index, page in enumerate(doc):
                text = page.get_text("text").strip()

                original_page = page_index + 1

                is_scanned = len(text) < text_threshold and len(page.get_images()) > 0

                if is_scanned:
                    scanned_doc.insert_pdf(
                        doc, from_page=page_index, to_page=page_index
                    )
                    scanned_map[len(scanned_doc)] = original_page
                else:
                    digital_doc.insert_pdf(
                        doc, from_page=page_index, to_page=page_index
                    )
                    digital_map[len(digital_doc)] = original_page

        return {
            "digital": (digital_doc, digital_map),
            "scanned": (scanned_doc, scanned_map),
        }
    except Exception as e:
        digital_doc.close()
        scanned_doc.close()
        logger.error(f"Failed to process PDF {file_path}: {e}")
        raise
