"""Single source of truth for offline/online feature calculation.

All features are computed as of application_date; labels or decisions never enter X.
The principal-only payment is a *proxy*, not a quoted loan installment.
"""

from __future__ import annotations

import math

import pandas as pd

from .schemas import ScoringInput

CATEGORY_FEATURES = ("industry", "product_type")
FEATURE_COLUMNS = (
    "industry",
    "product_type",
    "company_age_months",
    "avg_monthly_revenue_rub",
    "mandatory_expense_ratio",
    "existing_payment_ratio",
    "free_cashflow_rub",
    "has_active_loans",
    "past_overdue_count",
    "requested_amount_rub",
    "term_months",
    "requested_payment_principal_proxy_rub",
    "payment_burden_ratio",
    "loan_to_revenue_6m_ratio",
    "affordability_coverage_proxy",
)
NUMERIC_FEATURES = tuple(x for x in FEATURE_COLUMNS if x not in CATEGORY_FEATURES)
INDUSTRIES = {"retail", "services", "construction", "manufacturing", "other"}


def _months_between(start, end) -> int:
    months = (end.year - start.year) * 12 + (end.month - start.month)
    return max(0, months - (end.day < start.day))


def build_features(payload: ScoringInput | dict) -> dict:
    """Deterministic mapping from API payload to one numerical/categorical feature row."""
    data = payload if isinstance(payload, ScoringInput) else ScoringInput.model_validate(payload)
    monthly_rev = data.revenue_6m_rub / 6.0
    expenses = data.monthly_mandatory_expenses_rub
    existing = data.existing_monthly_debt_payment_rub
    free_cf = monthly_rev - expenses - existing
    principal_proxy = data.requested_amount_rub / data.term_months
    denom_rev = max(monthly_rev, 1.0)
    industry = data.industry.strip().lower()
    if industry not in INDUSTRIES:
        industry = "other"  # normalized FNS categories; mapping must be maintained upstream

    row = {
        "industry": industry,
        "product_type": data.product_type,
        "company_age_months": _months_between(data.registration_date, data.application_date),
        "avg_monthly_revenue_rub": monthly_rev,
        "mandatory_expense_ratio": expenses / denom_rev,
        "existing_payment_ratio": existing / denom_rev,
        "free_cashflow_rub": free_cf,
        "has_active_loans": int(data.has_active_loans),
        "past_overdue_count": data.past_overdue_count,
        "requested_amount_rub": data.requested_amount_rub,
        "term_months": data.term_months,
        "requested_payment_principal_proxy_rub": principal_proxy,
        "payment_burden_ratio": (existing + principal_proxy) / denom_rev,
        "loan_to_revenue_6m_ratio": data.requested_amount_rub / max(data.revenue_6m_rub, 1.0),
        "affordability_coverage_proxy": max(-10.0, min(50.0, free_cf / max(principal_proxy, 1.0))),
    }
    if any(not math.isfinite(v) for v in row.values() if isinstance(v, (float, int))):
        raise ValueError("Non-finite scoring feature; check incoming finance data")
    return row


def batch_features(df: pd.DataFrame) -> pd.DataFrame:
    """Offline training uses identical validation and transformations as serving."""
    allowed = set(ScoringInput.model_fields)
    records = [build_features({k: v for k, v in row.items() if k in allowed})
               for row in df.to_dict(orient="records")]
    return pd.DataFrame.from_records(records, columns=list(FEATURE_COLUMNS))
