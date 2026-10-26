"""Train CatBoost on completed SYNTHETIC loan outcomes and save artifacts.

Usage: python -m ml.train --data data/historical_loans.csv --out models
Temporal split (applications from 2022-2023 / H1 2024 / H2 2024) avoids
training against later applications, and one company is simulated only once.
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd
from catboost import CatBoostClassifier
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from .features import CATEGORY_FEATURES, FEATURE_COLUMNS, NUMERIC_FEATURES, batch_features
from .metrics import binary_metrics, calibration_bins

LABEL = "default_12m"
VERSION = "synthetic-credit-risk-v1"
# DEMONSTRATION boundaries, not learned from or validated on real bank defaults.
RISK_THRESHOLDS = {"low_lt": 0.08, "medium_lt": 0.18}


def split_history(df: pd.DataFrame):
    when = pd.to_datetime(df["application_date"])
    train = df.loc[when < "2024-01-01"].copy()
    val = df.loc[(when >= "2024-01-01") & (when < "2024-07-01")].copy()
    test = df.loc[when >= "2024-07-01"].copy()
    for name, group in (("train", train), ("validation", val), ("test", test)):
        if len(group) < 40 or group[LABEL].nunique() != 2:
            raise ValueError(f"{name} split is too small or contains only one class")
    ids = [set(group.company_id) for group in (train, val, test)]
    if (ids[0] & ids[1]) or (ids[0] & ids[2]) or (ids[1] & ids[2]):
        raise ValueError("A company appears in more than one split: leakage")
    return train, val, test


def train(data_path: Path, out_dir: Path, iterations: int = 360, seed: int = 42) -> dict:
    df = pd.read_csv(data_path)
    if LABEL not in df or "application_date" not in df or "company_id" not in df:
        raise ValueError("Missing default_12m, company_id or application_date")
    if set(df[LABEL].unique()) != {0, 1}:
        raise ValueError("Expected observed binary default_12m labels")
    train_df, val_df, test_df = split_history(df)
    x_tr, x_val, x_test = (batch_features(group) for group in (train_df, val_df, test_df))
    y_tr, y_val, y_test = (group[LABEL].astype(int) for group in (train_df, val_df, test_df))

    cat = CatBoostClassifier(
        iterations=iterations, depth=5, learning_rate=0.055,
        loss_function="Logloss", eval_metric="AUC",
        random_seed=seed, verbose=False, allow_writing_files=False,
        thread_count=4,
    )
    cat.fit(x_tr, y_tr, cat_features=list(CATEGORY_FEATURES), eval_set=(x_val, y_val),
            early_stopping_rounds=45, use_best_model=True)

    baseline = Pipeline([
        ("preprocessing", ColumnTransformer([
            ("numeric", StandardScaler(), list(NUMERIC_FEATURES)),
            ("categorical", OneHotEncoder(handle_unknown="ignore"), list(CATEGORY_FEATURES)),
        ])),
        ("classifier", LogisticRegression(max_iter=1500, random_state=seed)),
    ])
    baseline.fit(x_tr, y_tr)

    scores = {}
    for split_name, x, y in [("validation", x_val, y_val), ("test", x_test, y_test)]:
        scores[split_name] = {
            "catboost": binary_metrics(y, cat.predict_proba(x)[:, 1]),
            "logistic_regression": binary_metrics(y, baseline.predict_proba(x)[:, 1]),
        }
    scores["test"]["catboost_calibration_bins"] = calibration_bins(y_test, cat.predict_proba(x_test)[:, 1])

    out_dir.mkdir(parents=True, exist_ok=True)
    cat.save_model(str(out_dir / "model.cbm"))
    joblib.dump(baseline, out_dir / "logreg_baseline.joblib")
    metadata = {
        "model_version": VERSION,
        "model_type": "CatBoostClassifier",
        "origin": "fully_synthetic_simulation",
        "target": "90+ days past due within 12 months of loan issue (synthetic)",
        "feature_columns": list(FEATURE_COLUMNS),
        "category_features": list(CATEGORY_FEATURES),
        "risk_thresholds": RISK_THRESHOLDS,
        "training_date_utc": datetime.now(timezone.utc).isoformat(),
        "train_rows": int(len(train_df)),
        "validation_rows": int(len(val_df)),
        "test_rows": int(len(test_df)),
        "iterations_requested": int(iterations),
        "best_iteration": int(cat.best_iteration_),
        "warning": "Synthetic metrics only. NOT calibrated or validated on real banking data.",
    }
    (out_dir / "metadata.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    (out_dir / "metrics.json").write_text(json.dumps(scores, indent=2, ensure_ascii=False), encoding="utf-8")
    return {"metadata": metadata, "metrics": scores}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=Path("data/historical_loans.csv"))
    parser.add_argument("--out", type=Path, default=Path("models"))
    parser.add_argument("--iterations", type=int, default=360)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    result = train(args.data, args.out, args.iterations, args.seed)
    print(json.dumps({"model_version": result["metadata"]["model_version"],
                      "test_metrics": result["metrics"]["test"]["catboost"]},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
