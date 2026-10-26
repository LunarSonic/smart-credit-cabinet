"""Юнит-тесты DecisionEngine — все решения без обращения к БД или ML-модели"""

from datetime import date
from pathlib import Path

import pytest

from decision.engine import DecisionEngine
from decision.policy import load_policy
from decision.schemas import VerificationData
from ml.schemas import ScoringInput, ScoringResult

# ─── Вспомогательные фабрики ─────────────────────────────────────────────────

POLICY_PATH = Path(__file__).parent.parent / "decision" / "policy.json"
APP_ID = "test-app-001"


def _policy():
    return load_policy(POLICY_PATH)


def _engine():
    return DecisionEngine(policy_path=POLICY_PATH)


def _scoring_input(**overrides) -> ScoringInput:
    defaults = dict(
        application_id=APP_ID,
        application_date=date(2026, 10, 1),
        registration_date=date(2020, 3, 15),
        industry="47.11",
        product_type="working_capital",
        revenue_6m_rub=1_200_000.0,
        monthly_mandatory_expenses_rub=50_000.0,
        has_active_loans=False,
        existing_monthly_debt_payment_rub=0.0,
        past_overdue_count=0,
        requested_amount_rub=180_000.0,
        term_months=12,
    )
    defaults.update(overrides)
    return ScoringInput.model_validate(defaults)


def _scoring_result(risk_level: str = "low") -> ScoringResult:
    return ScoringResult(
        application_id=APP_ID,
        risk_level=risk_level,
        predicted_pd_12m={"low": 0.05, "medium": 0.35, "high": 0.75}[risk_level],
        risk_factors=[],
        model_version="test-1.0",
        scoring_backend="catboost",
    )


def _verifications(**overrides) -> VerificationData:
    defaults = dict(
        company_status="active",
        representative_authorized=True,
        personal_data_consent=True,
        credit_history_consent=True,
        fns_check="ok",
        bki_check="ok",
    )
    defaults.update(overrides)
    return VerificationData.model_validate(defaults)


# ─── Тесты ──────────────────────────────────────────────────────────────────

class TestDecisionEngineApprove:
    def test_approve_low_risk_good_cashflow(self):
        # Низкий риск, достаточный поток → одобрение
        result = _engine().evaluate(_scoring_input(), _scoring_result("low"), _verifications())
        assert result.recommendation == "approve"
        assert result.offer is not None
        assert result.offer.amount_rub == pytest.approx(180_000, rel=1e-3)

    def test_approve_creates_offer_with_correct_term(self):
        result = _engine().evaluate(
            _scoring_input(term_months=6),
            _scoring_result("low"),
            _verifications(),
        )
        assert result.recommendation == "approve"
        assert result.offer.term_months == 6


class TestDecisionEngineAlternativeOffer:
    def test_alternative_offer_when_requested_amount_too_high(self):
        # Высокая запрашиваемая сумма при низком свободном потоке → альтернативные условия
        # avg_monthly = 50 000; expenses = 30 000 → free = 20 000; max_payment = 12 000
        result = _engine().evaluate(
            _scoring_input(revenue_6m_rub=300_000, monthly_mandatory_expenses_rub=30_000,
                           requested_amount_rub=400_000),
            _scoring_result("low"),
            _verifications(),
        )
        assert result.recommendation == "alternative_offer"
        assert result.offer is not None
        assert result.offer.amount_rub < 400_000

    def test_alternative_offer_amount_above_product_min(self):
        # Предлагаемая сумма должна быть не меньше минимума продукта (50 000 ₽)
        result = _engine().evaluate(
            _scoring_input(revenue_6m_rub=300_000, monthly_mandatory_expenses_rub=30_000,
                           requested_amount_rub=400_000),
            _scoring_result("low"),
            _verifications(),
        )
        if result.recommendation == "alternative_offer":
            assert result.offer.amount_rub >= 50_000


class TestDecisionEngineReject:
    def test_reject_when_no_free_cashflow(self):
        # Расходы превышают выручку → нет свободных средств → отказ
        result = _engine().evaluate(
            _scoring_input(revenue_6m_rub=120_000, monthly_mandatory_expenses_rub=22_000),
            _scoring_result("low"),
            _verifications(),
        )
        assert result.recommendation == "reject"
        assert "NO_FREE_CASHFLOW" in result.reason_codes

    def test_reject_inactive_company(self):
        result = _engine().evaluate(
            _scoring_input(),
            _scoring_result("low"),
            _verifications(company_status="inactive"),
        )
        assert result.recommendation == "reject"
        assert "COMPANY_INACTIVE" in result.reason_codes

    def test_reject_below_product_minimum(self):
        result = _engine().evaluate(
            _scoring_input(requested_amount_rub=5_000),
            _scoring_result("low"),
            _verifications(),
        )
        assert result.recommendation == "reject"
        assert "BELOW_PRODUCT_MINIMUM" in result.reason_codes


class TestDecisionEngineManualReview:
    def test_manual_review_high_risk(self):
        # Политика: high_risk_action = manual_review
        result = _engine().evaluate(_scoring_input(), _scoring_result("high"), _verifications())
        assert result.recommendation == "manual_review"
        assert "HIGH_RISK" in result.reason_codes

    def test_manual_review_when_fns_unavailable(self):
        result = _engine().evaluate(
            _scoring_input(),
            _scoring_result("low"),
            _verifications(fns_check="unavailable"),
        )
        assert result.recommendation == "manual_review"
        assert "EXTERNAL_CHECK_UNAVAILABLE" in result.reason_codes

    def test_manual_review_when_bki_unavailable(self):
        result = _engine().evaluate(
            _scoring_input(),
            _scoring_result("low"),
            _verifications(bki_check="unavailable"),
        )
        assert result.recommendation == "manual_review"


class TestDecisionEngineNeedsInfo:
    def test_needs_info_when_personal_data_consent_missing(self):
        result = _engine().evaluate(
            _scoring_input(),
            _scoring_result("low"),
            _verifications(personal_data_consent=False),
        )
        assert result.recommendation == "needs_info"
        assert "CONSENT_MISSING" in result.reason_codes
        assert "personal_data_consent" in result.missing_items

    def test_needs_info_when_credit_history_consent_missing(self):
        result = _engine().evaluate(
            _scoring_input(),
            _scoring_result("low"),
            _verifications(credit_history_consent=False),
        )
        assert result.recommendation == "needs_info"
        assert "credit_history_consent" in result.missing_items

    def test_needs_info_when_both_consents_missing(self):
        result = _engine().evaluate(
            _scoring_input(),
            _scoring_result("low"),
            _verifications(personal_data_consent=False, credit_history_consent=False),
        )
        assert result.recommendation == "needs_info"
        assert len(result.missing_items) == 2


class TestDecisionEngineContract:
    def test_result_always_has_policy_version(self):
        result = _engine().evaluate(_scoring_input(), _scoring_result("low"), _verifications())
        assert result.policy_version == _policy().version

    def test_offer_only_for_approve_and_alternative(self):
        """Оферта присутствует только при approve или alternative_offer."""
        for risk in ("low", "medium", "high"):
            result = _engine().evaluate(_scoring_input(), _scoring_result(risk), _verifications())
            if result.recommendation in {"approve", "alternative_offer"}:
                assert result.offer is not None
            else:
                assert result.offer is None

    def test_mismatched_application_id_raises(self):
        scoring = ScoringResult(
            application_id="other-id",
            risk_level="low",
            predicted_pd_12m=0.05,
            risk_factors=[],
            model_version="test-1.0",
            scoring_backend="catboost",
        )
        with pytest.raises(ValueError, match="application_id"):
            _engine().evaluate(_scoring_input(), scoring, _verifications())

    def test_wrong_scoring_backend_raises(self):
        from ml.schemas import ScoringResult as SR
        # Pydantic's Literal prevents creating a non-catboost ScoringResult directly;
        # validate via model_construct to bypass validation, then check engine rejects it.
        bad_scoring = SR.model_construct(
            application_id=APP_ID,
            risk_level="low",
            predicted_pd_12m=0.05,
            risk_factors=[],
            model_version="test-1.0",
            scoring_backend="logreg",  # not catboost
        )
        with pytest.raises(ValueError, match="(?i)catboost"):
            _engine().evaluate(_scoring_input(), bad_scoring, _verifications())
