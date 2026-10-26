from __future__ import annotations

import math
from pathlib import Path

from ml.schemas import ScoringInput, ScoringResult

from .affordability import annuity_payment, max_sustainable_payment, principal_for_payment
from .policy import DecisionPolicy, load_policy
from .schemas import CreditOffer, DecisionResult, VerificationData


class DecisionEngine:
    def __init__(self, policy_path: str | Path | None = None, policy: DecisionPolicy | None = None):
        if policy is not None and policy_path is not None:
            raise ValueError("Specify either policy or policy_path")
        self.policy = policy if policy is not None else load_policy(policy_path)

    def evaluate(
        self,
        application: ScoringInput | dict,
        scoring: ScoringResult | dict,
        verifications: VerificationData | dict,
    ) -> DecisionResult:
        application = (application if isinstance(application, ScoringInput)
                       else ScoringInput.model_validate(application))
        scoring = scoring if isinstance(scoring, ScoringResult) else ScoringResult.model_validate(scoring)
        verifications = (verifications if isinstance(verifications, VerificationData)
                         else VerificationData.model_validate(verifications))

        if scoring.application_id != application.application_id:
            raise ValueError("Mismatched application_id between scoring and application")
        if scoring.scoring_backend != "catboost":
            raise ValueError("Decision Engine accepts the configured CatBoost demo model only")

        def output(action: str, codes: list[str], message: str, *,
                   offer: CreditOffer | None = None, missing_items: list[str] | None = None,
                   max_payment: float | None = None) -> DecisionResult:
            return DecisionResult(
                application_id=application.application_id,
                recommendation=action,
                reason_codes=codes,
                explanation=message,
                offer=offer,
                missing_items=missing_items or [],
                maximum_new_payment_rub=(None if max_payment is None else round(max_payment, 2)),
                policy_version=self.policy.version,
                scoring_model_version=scoring.model_version,
            )

        # Authoritative eligibility gates come before risk or affordability.
        missing = []
        if not verifications.personal_data_consent:
            missing.append("personal_data_consent")
        if not verifications.credit_history_consent:
            missing.append("credit_history_consent")
        if missing:
            return output("needs_info", ["CONSENT_MISSING"],
                          "Требуются согласия для рассмотрения заявки.", missing_items=missing)

        if verifications.fns_check == "unavailable" or verifications.bki_check == "unavailable":
            return output("manual_review", ["EXTERNAL_CHECK_UNAVAILABLE"],
                          "Внешняя проверка недоступна; автоматическое решение не принимается.")
        if verifications.fns_check != "ok" or verifications.bki_check != "ok":
            pending = [name for name, status in (("fns_check", verifications.fns_check),
                                                ("bki_check", verifications.bki_check)) if status != "ok"]
            return output("needs_info", ["CHECK_PENDING"],
                          "Не завершены обязательные проверки.", missing_items=pending)

        if verifications.company_status == "inactive":
            return output("reject", ["COMPANY_INACTIVE"],
                          "Компания не имеет действующего регистрационного статуса.")
        if verifications.representative_authorized is False:
            return output("reject", ["REPRESENTATIVE_NOT_AUTHORIZED"],
                          "У заявителя отсутствуют подтверждённые полномочия.")
        if verifications.company_status == "unknown" or verifications.representative_authorized is None:
            unknown = []
            if verifications.company_status == "unknown":
                unknown.append("company_status")
            if verifications.representative_authorized is None:
                unknown.append("representative_authorized")
            return output("needs_info", ["REGISTRATION_OR_AUTHORITY_UNKNOWN"],
                          "Недостаточно подтверждённых регистрационных данных.", missing_items=unknown)

        product = self.policy.products[application.product_type]
        if application.requested_amount_rub < product.min_amount_rub:
            return output("reject", ["BELOW_PRODUCT_MINIMUM"],
                          "Сумма меньше минимальной для выбранного кредитного продукта.")

        if scoring.risk_level == "high":
            return output(self.policy.high_risk_action, ["HIGH_RISK"],
                          "Высокая оценка риска по синтетической модели; требуется экспертное рассмотрение."
                          if self.policy.high_risk_action == "manual_review" else
                          "Высокая категория риска по демонстрационной политике.")
        if scoring.risk_level == "medium" and self.policy.medium_risk_action == "manual_review":
            return output("manual_review", ["MEDIUM_RISK_REVIEW"],
                          "Заявка направлена на экспертное рассмотрение по правилам продукта.")

        term = max(product.min_term_months, min(application.term_months, product.max_term_months))
        max_payment = max_sustainable_payment(
            application.revenue_6m_rub,
            application.monthly_mandatory_expenses_rub,
            application.existing_monthly_debt_payment_rub,
            self.policy.max_new_payment_fraction_of_free_cashflow,
        )
        if max_payment == 0:
            return output("reject", ["NO_FREE_CASHFLOW"],
                          "После обязательных расходов и действующих платежей нет свободного денежного потока.",
                          max_payment=max_payment)

        sustainable_principal = principal_for_payment(
            max_payment, product.demo_annual_interest_rate, term
        )
        max_offer = min(application.requested_amount_rub, product.max_amount_rub, sustainable_principal)
        step = self.policy.amount_rounding_step_rub
        if max_offer < application.requested_amount_rub or term != application.term_months:
            max_offer = math.floor((max_offer + 1e-9) / step) * step
        if max_offer + 1e-6 < product.min_amount_rub:
            return output("reject", ["INSUFFICIENT_AFFORDABILITY"],
                          "Доступный платёж не позволяет предложить даже минимальную сумму продукта.",
                          max_payment=max_payment)
        if max_offer <= 0:
            return output("reject", ["INSUFFICIENT_AFFORDABILITY"],
                          "Недостаточный денежный поток для финансирования.", max_payment=max_payment)

        installment = annuity_payment(max_offer, product.demo_annual_interest_rate, term)
        # Guard floating point edge cases: never propose a payment beyond the budget.
        if installment > max_payment + 0.01:
            max_offer = math.floor((max_offer - step) / step) * step
            if max_offer < product.min_amount_rub:
                return output("reject", ["INSUFFICIENT_AFFORDABILITY"],
                              "Недостаточно средств для минимального кредитного платежа.",
                              max_payment=max_payment)
            installment = annuity_payment(max_offer, product.demo_annual_interest_rate, term)
        offer = CreditOffer(
            amount_rub=round(max_offer, 2),
            term_months=term,
            annual_interest_rate=product.demo_annual_interest_rate,
            estimated_monthly_payment_rub=round(installment, 2),
        )
        if max_offer + 1e-6 < application.requested_amount_rub or term != application.term_months:
            codes = ["ALTERNATIVE_TERMS"]
            if max_offer < application.requested_amount_rub:
                codes.append("AMOUNT_REDUCED")
            if term != application.term_months:
                codes.append("TERM_ADJUSTED")
            return output("alternative_offer", codes,
                          "Возможен кредит на изменённых условиях с учётом финансовых показателей и лимитов продукта.",
                          offer=offer, max_payment=max_payment)
        return output("approve", ["WITHIN_POLICY_LIMITS"],
                      "Запрашиваемые условия соответствуют демонстрационной политике, требуется подтверждение сотрудника.",
                      offer=offer, max_payment=max_payment)
