import pandas as pd

from utils.string import clean_string


def linearize_table(df: pd.DataFrame) -> list[list[str]]:

    linearize_rows: list[list[str]] = []

    df = df.ffill(axis=0).ffill(axis=1)

    columns = df.columns

    for row in df.itertuples(index=False):
        row_statements = []

        for i in range(len(columns)):
            cleaned_statement = clean_string(str(row[i]).strip())
            row_statements.append(cleaned_statement)

        if row_statements:
            linearize_rows.append(row_statements)

    return linearize_rows
