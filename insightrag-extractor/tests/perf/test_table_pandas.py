import time

import numpy as np
import pandas as pd

rows = 10**5
df = pd.DataFrame(
    {
        "Title 1": np.random.choice(["Content 1", "Content 2", "Content 3"], size=rows),
        "Title 2": np.random.choice(["Content 4", "Content 5", "Content 6"], size=rows),
        "Title 3": np.random.choice(["Content 7", "Content 8", "Content 9"], size=rows),
    }
)


df = df.ffill(axis=0).ffill(axis=1)


def bench_iterrows(df: pd.DataFrame) -> list[str]:
    cols = df.columns
    out = []
    for _, row in df.iterrows():
        stmts = [f"{col}: {str(row[col]).strip()}" for col in cols]
        out.append("- [Dữ liệu bảng] " + " | ".join(stmts))
    return out


def bench_apply(df: pd.DataFrame) -> list[str]:
    def format_row(row):
        stmts = [f"{col}: {str(val).strip()}" for col, val in row.items()]
        return "- [Dữ liệu bảng] " + " | ".join(stmts)

    return df.apply(format_row, axis=1).tolist()


def bench_itertuples(df: pd.DataFrame) -> list[str]:
    cols = df.columns
    out = []
    for row in df.itertuples(index=False):
        stmts = [f"{cols[i]}: {str(row[i]).strip()}" for i in range(len(cols))]
        out.append("- [Dữ liệu bảng] " + " | ".join(stmts))
    return out


def bench_to_dict(df: pd.DataFrame) -> list[str]:
    records = df.to_dict(orient="records")
    cols = df.columns
    return [
        "- [Dữ liệu bảng] " + " | ".join([f"{c}: {str(r[c]).strip()}" for c in cols])
        for r in records
    ]


methods = {
    "iterrows": bench_iterrows,
    "apply": bench_apply,
    "itertuples": bench_itertuples,
    "to_dict": bench_to_dict,
}

results = []

for name, func in methods.items():
    start = time.perf_counter()
    _ = func(df)
    elapsed = time.perf_counter() - start

    results.append({"Method": name, "Time (seconds)": round(elapsed, 4)})

results_df = pd.DataFrame(results)
print(results_df.to_string(index=False))
