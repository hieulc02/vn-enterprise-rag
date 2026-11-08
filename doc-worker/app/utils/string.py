import re

import unicodedata
from decimal import Decimal


from babel.numbers import parse_decimal, NumberFormatError


def clean_string(text: str) -> str:
    """Applies NFKC unicode normalization to clean special characters (\xa0)."""
    if not isinstance(text, str):
        raise TypeError(f"Expected str, got {type(text).__name__}")
    return unicodedata.normalize("NFKC", text)


def parse_numeric_value(raw_value: str, locale: str = "vi_VN") -> Decimal | None:
    """
    Strictly parses a financial string into exact Decimal.

    Return None for valid formats, missing data to prevent silent data
    corruption in the knowledge graph.
    """

    if not isinstance(raw_value, str):
        return None

    value = clean_string(raw_value)

    if not value or value.lower in ["-", "n/a", "nan", "null"]:
        return None

    is_negative = False
    # Handles accounting negative formats: "(1000)" or "-1.000"
    if value.startswith("(") and value.endswith(")"):
        is_negative = True
        value = value[1:-1].strip()
    elif value.startswith("-") or value.startswith(
        "−"
    ):  # Captures both hyphen and unicode minus
        is_negative = True
        value = value[1:].strip()

    # Strips common currency markers
    value = re.sub(
        r"^(?:[A-Z]{3}|[$€£₫¥])\s*|\s*(?:[A-Z]{3}|[$€£₫¥])$",
        "",
        value,
        flags=re.IGNORECASE,
    ).strip()

    # The remaining string must contain only digits, spaces, and separators.
    if not re.fullmatch(r"[\d.,\s]+", value):
        return None

    try:
        parsed_decimal = parse_decimal(value, locale=locale)
        return -parsed_decimal if is_negative else parsed_decimal
    except (NumberFormatError, ValueError, Exception) as e:
        return None
