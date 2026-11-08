import pytest

from unittest.mock import patch, MagicMock
from app.pipeline.router import document_page_classify


def test_document_page_classify_mixed_document(mock_fitz_open):
    result = document_page_classify("mixed.pdf", text_threshold=100)

    digital_doc, digital_map = result["digital"]
    scanned_doc, scanned_map = result["scanned"]

    assert len(digital_doc) == 2
    assert digital_map == {1: 1, 2: 3}

    assert len(scanned_doc) == 2
    assert scanned_map == {1: 2, 2: 4}


@patch("app.pipeline.router.fitz.open")
def test_document_page_classify_cleans_up_on_exception(mock_fitz_open):
    mock_digital_doc = MagicMock()
    mock_scanned_doc = MagicMock()

    mock_fitz_open.side_effect = [
        mock_digital_doc,
        mock_scanned_doc,
        RuntimeError("Corrupted PDF"),
    ]

    with pytest.raises(RuntimeError, match="Corrupted PDF"):
        # Because the exceptions happens inside `fizt.open("error.pdf")`,
        # we need to ensure the empty digital/scanned docs instantiated
        # beforehand are properly closed.
        document_page_classify("error.pdf")

    mock_digital_doc.close.assert_called_once()
    mock_scanned_doc.close.assert_called_once()
