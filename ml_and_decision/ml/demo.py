"""Score the example entrepreneur from page 8 of Ум.pdf.

Only revenue, age, amount, term and business description come from the PDF.
All expenses and mock BKI details below are an explicitly invented demo fixture.
"""

import argparse
from pathlib import Path

from .inference import CreditRiskScorer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-dir", default="models", type=Path)
    args = parser.parse_args()
    demo_request = {
        "application_id": "DEMO-VICTORIA-001",
        "application_date": "2026-10-25",
        "registration_date": "2025-04-25",
        "industry": "retail",
        "product_type": "working_capital",
        "revenue_6m_rub": 900_000,
        "monthly_mandatory_expenses_rub": 90_000,  # invented, not present in the PDF
        "has_active_loans": False,                 # invented, not present in the PDF
        "existing_monthly_debt_payment_rub": 0,
        "past_overdue_count": 0,                 # invented, not present in the PDF
        "requested_amount_rub": 180_000,
        "term_months": 12,
    }
    result = CreditRiskScorer(args.model_dir).predict(demo_request)
    print(result.model_dump_json(indent=2))


if __name__ == "__main__":
    main()
