import os
import pytest
from faker import Faker
from unittest.mock import patch

os.environ.setdefault("TESTCONTAINERS_RYUK_DISABLED", "true")


@pytest.fixture
def fake():
    fake_instance = Faker(locale="vi_VN")
    Faker.seed(0)
    return fake_instance


# Fake PyMuPDF Classes for Testing
class FakeFitzPage:
    def __init__(self, text: str, has_image: bool):
        self.text = text
        self.has_image = has_image

    def get_text(self, *args, **kwargs):
        return self.text

    def get_images(self, *args, **kwargs):
        return ["mock_image_data"] if self.has_image else []


class FakeFitzDoc:
    def __init__(self, pages=None):
        self._pages = pages or []
        self.is_closed = False

    def __iter__(self):
        return iter(self._pages)

    def __len__(self):
        return len(self._pages)

    # to set a resource and define what is returned when a class is used inside a `with` statement
    def __enter__(self):
        return self

    # to handle automatic cleanup duties and resource teardown
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def insert_pdf(self, src, from_page, to_page):
        self._pages.append(src._pages[from_page])

    def close(self):
        self.is_closed = True


@pytest.fixture
def mock_fitz_open():

    def open_side_effect(file_path=None, **kwargs):
        if file_path == "mixed.pdf":
            return FakeFitzDoc(
                pages=[
                    FakeFitzPage("A" * 150, False),  # digital
                    FakeFitzPage("OCR", True),  # scanned
                    FakeFitzPage("", False),  # digital
                    FakeFitzPage("A" * 10, True),  # scanned
                ]
            )
        elif file_path == "error.pdf":
            raise RuntimeError("Corrupted PDF")
        else:
            return FakeFitzDoc()

    with patch("app.pipeline.router.fitz.open", side_effect=open_side_effect):
        yield


# Mock LLM call
from app.config.config import ValidationSettings
from unittest.mock import MagicMock, AsyncMock
from app.llm.base import BaseLLMProvider


@pytest.fixture
def config():
    return ValidationSettings(
        max_concurrent=2, max_retries=3, base_wait_sec=1, batch_size=2
    )


@pytest.fixture
def mock_llm():
    llm = MagicMock(spec=BaseLLMProvider)
    llm.generate_json = AsyncMock()
    return llm
