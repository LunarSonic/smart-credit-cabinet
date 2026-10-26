import argparse

from ml.inference import CreditRiskScorer

from .engine import DecisionEngine
from .staff_actions import StaffActionValidator


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", default="models")
    args = parser.parse_args()

    application = {
        "application_id": "DEMO-VICTORIA-001",
        "application_date": "2026-10-25",
        "registration_date": "2025-04-25",
        "industry": "retail",
        "product_type": "working_capital",
        "revenue_6m_rub": 900000,
        "monthly_mandatory_expenses_rub": 90000,
        "has_active_loans": False,
        "existing_monthly_debt_payment_rub": 0,
        "past_overdue_count": 0,
        "requested_amount_rub": 180000,
        "term_months": 12,
    }
    checks = {
        "company_status": "active",
        "representative_authorized": True,
        "personal_data_consent": True,
        "credit_history_consent": True,
        "fns_check": "ok",
        "bki_check": "ok",
    }
    scorer = CreditRiskScorer(args.model_dir)
    engine = DecisionEngine()
    score = scorer.predict(application)
    preliminary = engine.evaluate(application, score, checks)
    print("SCORING:", score.model_dump_json(indent=2))
    print("PRELIMINARY RECOMMENDATION:", preliminary.model_dump_json(indent=2))
    staff_action = {
        "staff_id": "demo-employee-1",
        "action": "alternative_offer",
        "reason": "Демонстрационное предложение уменьшенной суммы по TS-06",
        "offered_amount_rub": 150000,
        "offered_term_months": 12,
    }
    validated = StaffActionValidator(engine).validate(application, score, checks, staff_action)
    print("STAFF ACTION VALIDATION (NOT PERSISTED):", validated.model_dump_json(indent=2))


if __name__ == "__main__":
    main()
