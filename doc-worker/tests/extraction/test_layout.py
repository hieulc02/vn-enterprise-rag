import pytest

from app.extraction.layout import DocumentHierarchyTracker, Hierarchy


@pytest.fixture
def tracker() -> DocumentHierarchyTracker:
    return DocumentHierarchyTracker()


@pytest.mark.parametrize(
    "text, expected",
    [
        ("1.1", Hierarchy.MULTI_NUMERIC),
        ("1.2.3.4", Hierarchy.MULTI_NUMERIC),
        ("1.2. Section", Hierarchy.MULTI_NUMERIC),
        ("1. Heading", Hierarchy.NUMERIC),
        ("99. Section", Hierarchy.NUMERIC),
        ("A. Heading", Hierarchy.ALPHA_UPPER),
        ("B. Section", Hierarchy.ALPHA_UPPER),
        ("a. Heading 1", Hierarchy.ALPHA_LOWER),
        ("à. Heading 2", Hierarchy.ALPHA_LOWER),
        ("b. Section", Hierarchy.ALPHA_LOWER),
        ("II. Heading", Hierarchy.ROMAN_UPPER),
        ("IV. Section", Hierarchy.ROMAN_UPPER),
        ("i. Heading", Hierarchy.ROMAN_LOWER),
        ("iv. Section", Hierarchy.ROMAN_LOWER),
        ("standard sentence", Hierarchy.UNKNOWN),
        ("1.Section", Hierarchy.UNKNOWN),
    ],
)
def test_identify_hierarchy(
    tracker: DocumentHierarchyTracker, text: str, expected: Hierarchy
):
    assert tracker._identify_hierarchy(text) == expected


def test_identify_roman_vs_alpha_disambiguation(tracker: DocumentHierarchyTracker):
    assert tracker._identify_hierarchy("I. Section 1") == Hierarchy.ROMAN_UPPER

    assert tracker._identify_hierarchy("H. Section 2") == Hierarchy.ALPHA_UPPER

    assert tracker._identify_hierarchy("I. Section 3") == Hierarchy.ALPHA_UPPER

    assert tracker._identify_hierarchy("J. Section 3") == Hierarchy.ALPHA_UPPER


def test_identify_roman_vs_alpha_lower_disambiguation(
    tracker: DocumentHierarchyTracker,
):
    assert tracker._identify_hierarchy("i. Section 1") == Hierarchy.ROMAN_LOWER

    assert tracker._identify_hierarchy("h. Section 2") == Hierarchy.ALPHA_LOWER

    assert tracker._identify_hierarchy("i. Section 3") == Hierarchy.ALPHA_LOWER


def test_get_level_empty_and_whitespace(tracker: DocumentHierarchyTracker):
    assert tracker.get_level("") == DocumentHierarchyTracker.UNKNOWN_LEVEL_FALLBACK
    assert (
        tracker.get_level(" \n\t ") == DocumentHierarchyTracker.UNKNOWN_LEVEL_FALLBACK
    )


def test_get_level_stateful_assignment(tracker: DocumentHierarchyTracker):
    assert tracker.get_level("I. Section 1") == 1
    assert tracker.get_level("A. Sub-section 1") == 2
    assert tracker.get_level("1. Detail 1") == 3

    assert tracker.get_level("II. Section 2") == 1
    assert tracker.get_level("B. Sub-section 2") == 2
    assert tracker.get_level("2. Detail 2") == 3


def test_get_level_fallback_unnumbered_upper(tracker: DocumentHierarchyTracker):
    level = tracker.get_level("SECTION")
    assert level == 1
    assert Hierarchy.UNNUMBERED_UPPER in tracker.section_levels


def test_get_level_fallback_unnumbered_title(tracker: DocumentHierarchyTracker):
    level_1 = tracker.get_level("Section 1")
    assert level_1 == 1
    assert Hierarchy.UNNUMBERED_TITLE in tracker.section_levels

    level_2 = tracker.get_level("Section 2")
    assert level_2 == 1
    assert Hierarchy.UNNUMBERED_TITLE in tracker.section_levels


def test_get_level_fallback_unknown_text(tracker: DocumentHierarchyTracker):
    assert tracker.get_level("normal sentence") == 0
    assert len(tracker.section_levels) == 0


def test_get_level_fallback_symbol(tracker: DocumentHierarchyTracker):
    assert tracker.get_level("?!@") == DocumentHierarchyTracker.UNKNOWN_LEVEL_FALLBACK
