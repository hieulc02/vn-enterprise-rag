import pandas as pd

from utils.string import clean_string


def linearize_table(df: pd.DataFrame) -> list[list[str]]:

    linearize_rows: list[list[str]] = []

    df = df.fillna("")

    columns = df.columns

    for row in df.itertuples(index=False):
        row_statements = []
        has_valid_data = False

        for i in range(len(columns)):
            cleaned_statement = clean_string(str(row[i]).strip())

            if cleaned_statement:
                has_valid_data = True

            row_statements.append(cleaned_statement)

        if has_valid_data:
            linearize_rows.append(row_statements)

    return linearize_rows
