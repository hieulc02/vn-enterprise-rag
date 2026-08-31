import re
from enum import StrEnum, auto

VI_UPPER = "A-ZÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝĂĐĨŨƠƯẠẢẤẦẨẪẬẮẰẲẴẶẸẺẼẾỀỂỄỆỈỊỌỎỐỒỔỖỘỚỜỞỠỢỤỦỨỪỬỮỰỲỴỶỸ"
VI_LOWER = "a-zàáâãèéêìíòóôõùúýăđĩũơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"


class Hierarchy(StrEnum):
    MULTI_NUMERIC = auto()
    ROMAN_UPPER = auto()
    ROMAN_LOWER = auto()
    ALPHA_UPPER = auto()
    ALPHA_LOWER = auto()
    NUMERIC = auto()
    UNNUMBERED_UPPER = auto()
    UNNUMBERED_TITLE = auto()
    UNKNOWN = auto()


class DocumentHierarchyTracker:

    _RX_MULTI_NUMERIC = re.compile(r"^\d+\.\d+(\.\d+)*\b")
    _RX_NUMERIC = re.compile(r"^\d+\.\s")

    _RX_ROMAN_UPPER = re.compile(r"^(I{1,3}|IV|V|VI{0,3}|IX|X)\.\s")
    _RX_ROMAN_LOWER = re.compile(r"^(i{1,3}|iv|v|vi{0,3}|ix|x)\.\s")

    _RX_ALPHA_UPPER = re.compile(rf"^([{VI_UPPER}])\.\s")
    _RX_ALPHA_LOWER = re.compile(rf"^([{VI_LOWER}])\.\s")

    UNKNOWN_LEVEL_FALLBACK = 0

    def __init__(self):
        self.section_levels: dict[Hierarchy, int] = {}
        self.next_level = 1

        self._last_alpha_upper: str | None = None
        self._last_alpha_lower: str | None = None

    def _identify_hierarchy(self, text: str) -> str:
        text = text.strip()

        # 1.1, 1.2.3
        if self._RX_MULTI_NUMERIC.match(text):
            return Hierarchy.MULTI_NUMERIC

        # 1. 2. 3.
        if self._RX_NUMERIC.match(text):
            return Hierarchy.NUMERIC

        # A. B. C.
        alpha_upper = self._RX_ALPHA_UPPER.match(text)
        if alpha_upper:
            char = alpha_upper.group(1)
            if char in ("I", "V", "X"):
                if (
                    self._last_alpha_upper
                    and ord(char) == ord(self._last_alpha_upper) + 1
                ):
                    self._last_alpha_upper = char
                    return Hierarchy.ALPHA_UPPER
                return Hierarchy.ROMAN_UPPER

            self._last_alpha_upper = char
            return Hierarchy.ALPHA_UPPER

        # a. b. c.
        alpha_low = self._RX_ALPHA_LOWER.match(text)
        if alpha_low:
            char = alpha_low.group(1)

            if char in ("i", "v", "x"):
                if (
                    self._last_alpha_lower
                    and ord(char) == ord(self._last_alpha_lower) + 1
                ):
                    self._last_alpha_lower = char
                    return Hierarchy.ALPHA_LOWER
                return Hierarchy.ROMAN_LOWER

            self._last_alpha_lower = char
            return Hierarchy.ALPHA_LOWER

        # I. II. III.
        if self._RX_ROMAN_UPPER.match(text):
            return Hierarchy.ROMAN_UPPER

        # i. ii. iv.
        if self._RX_ROMAN_LOWER.match(text):
            return Hierarchy.ROMAN_LOWER

        return Hierarchy.UNKNOWN

    def get_level(self, text: str) -> int:

        if not text or not text.strip():
            return self.UNKNOWN_LEVEL_FALLBACK

        section = self._identify_hierarchy(text)

        if section == Hierarchy.UNKNOWN:
            cleaned_first_word = "".join(c for c in text.split()[0] if c.isalpha())

            if text.isupper():
                section = Hierarchy.UNNUMBERED_UPPER
            elif cleaned_first_word.istitle() and len(cleaned_first_word) > 1:
                section = Hierarchy.UNNUMBERED_TITLE
            else:
                return self.UNKNOWN_LEVEL_FALLBACK

        if section not in self.section_levels:
            self.section_levels[section] = self.next_level
            self.next_level += 1

        return self.section_levels[section]
