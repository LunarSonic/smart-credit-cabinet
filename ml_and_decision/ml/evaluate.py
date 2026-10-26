"""Independent re-evaluation on synthetic hold-out data (no retraining)."""

import argparse
import json
from pathlib import Path

import joblib
import pandas as pd
from catboost import CatBoostClassifier

from .features import FEATURE_COLUMNS, batch_features
from .metrics import binary_metrics, calibration_bins
from .train import LABEL, split_history


def evaluate(data_path: Path, model_dir: Path) -> dict:
    df = pd.read_csv(data_path)
    _, _, test_df = split_history(df)
    x, y = batch_features(test_df), test_df[LABEL]
    metadata = json.loads((model_dir / "metadata.json").read_text(encoding="utf-8"))
    if metadata["feature_columns"] != list(FEATURE_COLUMNS):
        raise ValueError("Feature schema does not match trained artifact")
    model = CatBoostClassifier()
    model.load_model(str(model_dir / "model.cbm"))
    baseline = joblib.load(model_dir / "logreg_baseline.joblib")  # trusted local artifacts only
    p = model.predict_proba(x)[:, 1]
    b = baseline.predict_proba(x)[:, 1]
    return {"catboost": binary_metrics(y, p),
            "logistic_regression": binary_metrics(y, b),
            "catboost_calibration_bins": calibration_bins(y, p)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=Path("data/historical_loans.csv"))
    parser.add_argument("--model-dir", type=Path, default=Path("models"))
    args = parser.parse_args()
    print(json.dumps(evaluate(args.data, args.model_dir), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
