from __future__ import annotations

from ml.schemas import ScoringInput, ScoringResult

from .affordability import annuity_payment, max_sustainable_payment
from .engine import DecisionEngine
from .schemas import CreditOffer, StaffActionInput, ValidatedStaffAction, VerificationData


class StaffActionValidator:
    def __init__(self, engine: DecisionEngine):
        self.engine = engine

    def validate(self,
                 application: ScoringInput | dict,
                 scoring: ScoringResult | dict,
                 checks: VerificationData | dict,
                 action: StaffActionInput | dict) -> ValidatedStaffAction:
        app = application if isinstance(application, ScoringInput) else ScoringInput.model_validate(application)
        score = scoring if isinstance(scoring, ScoringResult) else ScoringResult.model_validate(scoring)
        checks = checks if isinstance(checks, VerificationData) else VerificationData.model_validate(checks)
        action = action if isinstance(action, StaffActionInput) else StaffActionInput.model_validate(action)
        preliminary = self.engine.evaluate(app, score, checks)

        if action.action == "reject":
            return self._result(app, action, None, "rejected")
        if action.action == "request_info":
            return self._result(app, action, None, "needs_clarification")

        # For a positive offer, ALL mandatory checks must be resolved and valid.
        if (
            checks.company_status != "active"
            or checks.representative_authorized is not True
            or checks.fns_check != "ok"
            or checks.bki_check != "ok"
            or not checks.personal_data_consent
            or not checks.credit_history_consent
        ):
            raise ValueError("Cannot issue positive offer before mandatory checks pass")

        if preliminary.recommendation in {"needs_info", "reject"}:
            raise ValueError("Cannot issue positive offer after unmet eligibility/affordability rules")
        if preliminary.recommendation == "manual_review" and not (action.reason or "").strip():
            raise ValueError("Manual risk override requires recorded justification")

        product = self.engine.policy.products[app.product_type]
        if action.action == "approve":
            amount, term = app.requested_amount_rub, app.term_months
        else:
            amount, term = action.offered_amount_rub, action.offered_term_months
            if amount == app.requested_amount_rub and term == app.term_months:
                raise ValueError("Alternative offer must change amount or term")
        assert amount is not None and term is not None
        if not (product.min_amount_rub <= amount <= product.max_amount_rub):
            raise ValueError("Offer amount is outside product limits")
        if not (product.min_term_months <= term <= product.max_term_months):
            raise ValueError("Offer term is outside product limits")
        if amount > app.requested_amount_rub:
            raise ValueError("Offer amount cannot exceed customer's requested amount")
        budget = max_sustainable_payment(
            app.revenue_6m_rub,
            app.monthly_mandatory_expenses_rub,
            app.existing_monthly_debt_payment_rub,
            self.engine.policy.max_new_payment_fraction_of_free_cashflow,
        )
        installment = annuity_payment(amount, product.demo_annual_interest_rate, term)
        if installment > budget + 0.01:
            raise ValueError("Offer installment exceeds demonstrated affordable budget")
        offer = CreditOffer(
            amount_rub=round(amount, 2),
            term_months=term,
            annual_interest_rate=product.demo_annual_interest_rate,
            estimated_monthly_payment_rub=round(installment, 2),
        )
        return self._result(app, action, offer, "proposed_terms")

    def _result(self, app, action, offer, status):
        return ValidatedStaffAction(
            application_id=app.application_id,
            staff_id=action.staff_id,
            action=action.action,
            reason=action.reason,
            offer=offer,
            suggested_workflow_status=status,
            policy_version=self.engine.policy.version,
        )
