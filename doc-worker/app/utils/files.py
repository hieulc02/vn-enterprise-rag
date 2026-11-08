from pathlib import Path


def build_filename_with_prefix_folder(
    folders: str, file_key: str, extension: str = ".json"
) -> str:

    if not file_key:
        raise ValueError("File key cannot be empty")

    ext = extension if extension.startswith(".") else f".{extension}"
    file_path = Path(file_key)
    base_name = file_path.stem
    folder_path = Path(folders.strip("/")) if folders else Path()
    final_path = folder_path / f"{base_name}{ext}"
    return final_path.as_posix()
