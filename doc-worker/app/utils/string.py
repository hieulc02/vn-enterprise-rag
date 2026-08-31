import re

import unicodedata

from babel.numbers import parse_decimal


def clean_string(text: str) -> str:
    """Applies NFKC unicode normalization to clean special characters (\xa0)."""
    if not isinstance(text, str):
        raise TypeError(f"Expected str, got {type(text).__name__}")
    return unicodedata.normalize("NFKC", text)


def is_numeric_string(val):
    try:
        float(val)
        return True
    except ValueError:
        return False


def parse_numeric_value(raw_value: str, locale: str = "vi_VN") -> float | str:

    value = raw_value.strip()

    if not value or value == "-":
        return 0.0

    is_negative = False
    if value.startswith("(") and value.endswith(")"):
        is_negative = True
        value = value[1:-1].strip()
    elif value.startswith("-"):
        is_negative = True

    value = re.sub(r"[^\d\.,]", "", value)
    if not value:
        return 0.0

    try:
        parsed_float = float(parse_decimal(value, locale=locale))
        return -parsed_float if is_negative else parsed_float
    except (ValueError, Exception) as e:
        return raw_value
