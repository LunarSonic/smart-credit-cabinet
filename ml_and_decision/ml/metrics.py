"""Shared evaluation functions for CatBoost and logistic baseline."""

import numpy as np
from sklearn.metrics import average_precision_score, brier_score_loss, log_loss, roc_auc_score


def binary_metrics(y_true, probs) -> dict:
    y = np.asarray(y_true, dtype=int)
    p = np.clip(np.asarray(probs, dtype=float), 1e-10, 1 - 1e-10)
    if len(np.unique(y)) != 2:
        raise ValueError("Evaluation split must contain both classes")
    return {
        "rows": int(len(y)),
        "positive_rate": float(y.mean()),
        "roc_auc": float(roc_auc_score(y, p)),
        "pr_auc": float(average_precision_score(y, p)),
        "brier": float(brier_score_loss(y, p)),
        "log_loss": float(log_loss(y, p)),
    }


def calibration_bins(y_true, probs, bins: int = 10) -> list[dict]:
    """Reliability bins, not a guarantee that predictions are calibrated."""
    y = np.asarray(y_true, dtype=int)
    p = np.asarray(probs, dtype=float)
    edges = np.linspace(0, 1, bins + 1)
    result = []
    for idx in range(bins):
        is_in = ((p >= edges[idx]) & (p < edges[idx + 1])) if idx < bins - 1 else ((p >= edges[idx]) & (p <= edges[idx + 1]))
        if is_in.any():
            result.append({"start": float(edges[idx]), "end": float(edges[idx + 1]),
                           "count": int(is_in.sum()), "mean_predicted_pd": float(p[is_in].mean()),
                           "default_rate": float(y[is_in].mean())})
    return result
