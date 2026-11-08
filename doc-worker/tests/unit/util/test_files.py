import pytest

from app.utils.files import build_filename_with_prefix_folder


@pytest.mark.parametrize(
    "folders, file_key, extension, expected",
    [
        ("raw_data", "doc123.pdf", ".json", "raw_data/doc123.json"),
        ("raw_data/", "doc123.pdf", ".json", "raw_data/doc123.json"),
        ("", "doc123.pdf", ".json", "doc123.json"),
        ("raw_data", "doc123.pdf", "json", "raw_data/doc123.json"),
        ("processed", "report.v2.final.pdf", ".json", "processed/report.v2.final.json"),
        ("bucket/folder", "doc123.pdf", "json", "bucket/folder/doc123.json"),
    ],
)
def test_build_filename(folders, file_key, extension, expected):
    result = build_filename_with_prefix_folder(folders, file_key, extension)
    assert result == expected
