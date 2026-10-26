"""Reproducible demo loan-history simulator. Labels are synthetic, NOT real PDs.

The document gives the form fields and two example products, but no real bank
interest rates/default rates. Every additional generator parameter is an explicit
hypothetical assumption. One company has one historical application, already
issued for the purpose of observing 12-month default labels.
"""

from __future__ import annotations

import argparse
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

PRODUCTS = {
    "working_capital": {"min_amount": 50_000, "max_amount": 500_000, "terms": [3, 6, 9, 12]},
    "equipment": {"min_amount": 100_000, "max_amount": 1_000_000, "terms": [6, 12, 18, 24, 36]},
}
INDUSTRIES = ["retail", "services", "construction", "manufacturing", "other"]


def sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-np.clip(x, -40, 40)))


def generate(rows: int = 50_000, seed: int = 42, target_default_rate: float = 0.10) -> pd.DataFrame:
    if rows < 100:
        raise ValueError("rows must be >= 100")
    if not 0 < target_default_rate < 1:
        raise ValueError("target_default_rate must be within (0, 1)")

    rng = np.random.default_rng(seed)
    industries = rng.choice(INDUSTRIES, size=rows, p=[0.35, 0.30, 0.10, 0.12, 0.13])
    products = rng.choice(["working_capital", "equipment"], size=rows, p=[0.72, 0.28])
    app_offsets = rng.integers(0, (date(2025, 1, 1) - date(2022, 1, 1)).days, size=rows)
    app_dates = [date(2022, 1, 1) + timedelta(days=int(d)) for d in app_offsets]

    company_age = np.clip(rng.gamma(shape=2.0, scale=23.0, size=rows).astype(int) + 3, 3, 240)
    registration_dates = [dt - timedelta(days=int(age * 30.5 + 10)) for dt, age in zip(app_dates, company_age)]

    monthly_revenue = np.clip(rng.lognormal(mean=np.log(150_000), sigma=0.66, size=rows), 15_000, 1_200_000)
    # Some enterprises temporarily run at a loss: expenses can exceed revenue.
    expense_ratio = np.clip(rng.beta(5.1, 2.5, size=rows) + rng.normal(0, 0.075, size=rows), 0.12, 1.28)
    monthly_expenses = monthly_revenue * expense_ratio

    fragility = (
        0.9 * (expense_ratio - 0.68) / 0.28
        + 0.3 * (company_age < 18)
        + 0.25 * (industries == "construction")
        + rng.normal(0, 0.85, size=rows)
    )
    loan_probability = sigmoid(-0.8 + 0.24 * np.log1p(company_age) + 0.1 * fragility)
    has_loans = rng.random(rows) < loan_probability
    existing_payment_ratio = np.clip(rng.beta(2, 10, size=rows), 0.015, 0.38)
    existing_payment = np.where(has_loans, monthly_revenue * existing_payment_ratio, 0.0)

    # Overdues are historical (available from the mocked BKI as of the application date).
    overdue_intensity = np.clip(0.13 * np.exp(np.clip(0.45 * fragility, -1.5, 1.7)), 0.01, 1.5)
    previous_overdues = np.minimum(rng.poisson(overdue_intensity), 7)

    requested_amount = np.zeros(rows, dtype=float)
    terms = np.zeros(rows, dtype=int)
    for product_name, config in PRODUCTS.items():
        mask = products == product_name
        n = int(mask.sum())
        requested_amount[mask] = np.clip(
            rng.lognormal(mean=np.log(180_000 if product_name == "working_capital" else 350_000),
                          sigma=0.57, size=n),
            config["min_amount"], config["max_amount"]
        )
        terms[mask] = rng.choice(config["terms"], size=n)

    # Unobserved factors and nonlinearities prevent a trivial deterministic target.
    free_cashflow = monthly_revenue - monthly_expenses - existing_payment
    principal_proxy = requested_amount / terms  # *not* a real loan installment
    burden = (existing_payment + principal_proxy) / monthly_revenue
    risk_signal = (
        1.4 * np.clip(expense_ratio - 0.65, -0.5, 0.6)
        + 0.9 * np.log1p(np.maximum(burden, 0))
        + 0.48 * previous_overdues
        + 0.45 * (company_age < 12)
        + 0.26 * (industries == "construction")
        + 0.28 * (free_cashflow < principal_proxy)
        + 0.38 * (requested_amount > monthly_revenue * 4)
        + rng.normal(0.0, 0.70, size=rows)
    )
    # Simulation intercept chosen to reach approx. target unconditional default rate.
    lo, hi = -15., 8.
    for _ in range(80):
        mid = (lo + hi) / 2.0
        if sigmoid(risk_signal + mid).mean() > target_default_rate:
            hi = mid
        else:
            lo = mid
    pd_simulated = sigmoid(risk_signal + (lo + hi) / 2.0)
    default_12m = (rng.random(rows) < pd_simulated).astype(int)

    # Only pre-application data + outcome are exported. The hidden risk and its
    # simulated probability must NEVER be features in model training.
    return pd.DataFrame({
        "company_id": [f"SYN{idx:07d}" for idx in range(rows)],
        "application_id": [f"APP{idx:07d}" for idx in range(rows)],
        "application_date": [dt.isoformat() for dt in app_dates],
        "registration_date": [dt.isoformat() for dt in registration_dates],
        "industry": industries,
        "product_type": products,
        "revenue_6m_rub": np.round(monthly_revenue * 6, 2),
        "monthly_mandatory_expenses_rub": np.round(monthly_expenses, 2),
        "has_active_loans": has_loans,
        "existing_monthly_debt_payment_rub": np.round(existing_payment, 2),
        "past_overdue_count": previous_overdues,
        "requested_amount_rub": np.round(requested_amount, 2),
        "term_months": terms,
        "default_12m": default_12m,
    })


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows", type=int, default=50_000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--default-rate", type=float, default=0.10)
    parser.add_argument("--output", type=Path, default=Path("data/historical_loans.csv"))
    args = parser.parse_args()
    df = generate(rows=args.rows, seed=args.seed, target_default_rate=args.default_rate)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(args.output, index=False)
    print(f"Saved {len(df):,} rows to {args.output}; synthetic 12m default rate={df.default_12m.mean():.3f}")


if __name__ == "__main__":
    main()
