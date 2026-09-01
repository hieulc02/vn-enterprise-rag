from pathlib import Path


def build_filename_with_prefix_folder(
    folders: str, file_key: str, extension: str = ".json"
) -> str:
    return f"{folders}/{Path(file_key).stem}{extension}"
