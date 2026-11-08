import pytest

import pandas as pd
import numpy as np

from app.extraction.transform import linearize_table


@pytest.fixture
def financial_df():
    return pd.DataFrame(
        {
            "Metric": ["Revenue", "Profit", np.nan],
            "Q1": [1000, 200, np.nan],
            "Q2": [np.nan, 250, np.nan],
        }
    )


def test_linearize_table_does_not_hallucinate_ffill(financial_df):
    result = linearize_table(financial_df)

    revenue_row = result[0]
    assert revenue_row[2] == ""


def test_linearize_table_handles_nan_without_string_cast(financial_df):
    result = linearize_table(financial_df)

    flat_results = [item for sublist in result for item in sublist]
    for cell in flat_results:
        assert "nan" not in cell.lower()


def test_linearize_table_filters_empty_rows(financial_df):
    result = linearize_table(financial_df)

    assert len(result) == 2
