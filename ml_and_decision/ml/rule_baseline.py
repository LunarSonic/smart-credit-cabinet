"""Optional deterministic local sanity-check baseline (NOT a PD model).

It does not participate in serving or the ML showcase. Every number below is an
invented expert-like demo assumption, not a real bank's underwriting policy.
"""

from .features import build_features
from .schemas import ScoringInput


def _clamp(x: float) -> float:
    return max(0., min(100., float(x)))


def rule_score(payload: ScoringInput | dict) -> dict:
    x = build_features(payload)
    sub_scores = {
        "past_overdues": _clamp(100 - 25 * x["past_overdue_count"]),
        "affordability": _clamp(100 * x["affordability_coverage_proxy"] / 3),
        "expenses": _clamp(100 * (1.05 - x["mandatory_expense_ratio"]) / 0.65),
        "existing_loans": _clamp(100 * (0.4 - x["existing_payment_ratio"]) / 0.4),
        "business_age": _clamp(100 * x["company_age_months"] / 36),
    }
    weights = {"past_overdues": .30, "affordability": .25, "expenses": .20,
               "existing_loans": .15, "business_age": .10}
    total = round(sum(weights[key] * sub_scores[key] for key in weights), 2)
    level = "low" if total >= 75 else ("medium" if total >= 50 else "high")
    return {"score_0_100": total, "risk_level": level,
            "factor_scores": {k: round(v, 2) for k, v in sub_scores.items()},
            "scoring_backend": "rule_based_local_only",
            "warning": "Heuristic 0-100 score, NOT a default probability."}
