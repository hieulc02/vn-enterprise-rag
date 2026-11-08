import pytest
from decimal import Decimal

from app.utils.string import clean_string, parse_numeric_value


class TestCleanString:

    @pytest.mark.parametrize(
        "input_str, expected",
        [
            ("Normal string", "Normal string"),
            ("100\xa0000", "100 000"),
            ("ﬁnancial", "financial"),
            ("２０２３", "2023"),
        ],
    )
    def test_clean_string_valid_input(self, input_str, expected):
        assert clean_string(input_str) == expected

    @pytest.mark.parametrize("invalid_input", [123, None, ["string in list"]])
    def test_clean_string_raises_type_error(self, invalid_input):
        with pytest.raises(TypeError, match="Expected str"):
            clean_string(invalid_input)


class TestParseNumericValue:

    @pytest.mark.parametrize(
        "input_value, expected, locale",
        [
            ("1.234.567", Decimal("1234567"), "vi_VN"),
            ("1.234.567,89", Decimal("1234567.89"), "vi_VN"),
            ("1,234,567.89", Decimal("1234567.89"), "en_US"),
            ("(1.000)", Decimal("-1000"), "vi_VN"),
            ("-1.000", Decimal("-1000"), "vi_VN"),
            ("−1.000", Decimal("-1000"), "vi_VN"),
            ("₫ 1.234.567", Decimal("1234567"), "vi_VN"),
            ("VND 1.234.567", Decimal("1234567"), "vi_VN"),
            ("$1,234.56", Decimal("1234.56"), "en_US"),
            ("1,234.56 USD", Decimal("1234.56"), "en_US"),
            ("€ 1.234,56", Decimal("1234.56"), "vi_VN"),
        ],
    )
    def test_parse_numeric_value_valid(self, input_value, expected, locale):
        result = parse_numeric_value(input_value, locale=locale)
        assert result == expected
        assert isinstance(result, Decimal)

    @pytest.mark.parametrize(
        "invalid_input",
        [
            "Q1 2023: 500",
            "1,500 shares",
            "Revenue was 1.000",
            "-",
            "N/A",
            "NaN",
            "null",
            "",
            "   ",
            None,
            "!@#$",
        ],
    )
    def test_parse_numeric_value_returns_none_for_invalid(self, invalid_input):
        assert parse_numeric_value(invalid_input, locale="vi_VN") is None
