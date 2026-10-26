"""POST /decide — интеграционный эндпоинт для Java-бэкенда.

Принимает MlDecideRequest, запускает ML-скоринг и Decision Engine,
возвращает MlDecideResponse. Формат контракта соответствует
MlDecideRequest.java и MlDecideResponse.java в backend/.
"""

from __future__ import annotations

import os
from datetime import date
from functools import lru_cache
from typing import Any
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from decision import DecisionEngine
from decision.schemas import VerificationData
from ml.schemas import ScoringInput

from .fastapi_router import load_scorer

router = APIRouter(tags=["backend-integration"])


# ── Загрузка Decision Engine ───────────────────────────────────────────────────

@lru_cache(maxsize=1)
def load_engine() -> DecisionEngine:
    policy_path = os.environ.get("DECISION_POLICY_PATH")
    return DecisionEngine(policy_path=policy_path)


# ── Pydantic-контракт (snake_case, как приходит от Java) ──────────────────────

class DecideRequest(BaseModel):
    application_id: UUID
    request_id: str
    product: dict[str, Any]
    application: dict[str, Any]
    company: dict[str, Any]
    account: dict[str, Any]
    bki: dict[str, Any]
    declared_obligations: dict[str, Any]
    checks: list[dict[str, Any]]


class DecideResponse(BaseModel):
    decision_id: str
    scoring_result_id: str
    score: int
    probability_of_default: float
    risk_class: str
    top_factors: list[dict[str, Any]]
    model_type: str
    model_version: str
    decision: str
    final_terms: dict[str, Any] | None = None
    changes: dict[str, Any] | None = None
    reasons: list[str]
    client_reasons: list[str]
    missing_data: list[str]
    policy_version: str


# ── Вспомогательные функции ────────────────────────────────────────────────────

_OKVED_TO_INDUSTRY: list[tuple[tuple[int, int], str]] = [
    ((10, 33), "manufacturing"),
    ((41, 43), "construction"),
    ((45, 47), "retail"),
    ((49, 99), "services"),
]


def _okved_to_industry(okved: str) -> str:
    try:
        code = int(str(okved).split(".")[0])
        for (lo, hi), name in _OKVED_TO_INDUSTRY:
            if lo <= code <= hi:
                return name
    except (ValueError, IndexError):
        pass
    return "other"


def _product_type(product_code: str) -> str:
    code = str(product_code).upper()
    if "WORKING" in code or "CAPITAL" in code:
        return "working_capital"
    if "EQUIP" in code:
        return "equipment"
    return "working_capital"


def _to_scoring_input(req: DecideRequest) -> ScoringInput:
    app = req.application
    company = req.company
    bki = req.bki

    try:
        reg_date = date.fromisoformat(str(company.get("registration_date", "")))
    except (ValueError, TypeError):
        reg_date = date(2020, 1, 1)

    return ScoringInput(
        application_id=str(req.application_id),
        application_date=date.today(),
        registration_date=reg_date,
        industry=_okved_to_industry(str(company.get("okved", ""))),
        product_type=_product_type(str(req.product.get("code", ""))),
        revenue_6m_rub=float(app.get("revenue_6m") or 0),
        monthly_mandatory_expenses_rub=float(app.get("monthly_expenses") or 0),
        has_active_loans=bool(app.get("has_loans", False)),
        existing_monthly_debt_payment_rub=float(app.get("loans_monthly_payment") or 0),
        past_overdue_count=int(bki.get("overdue_count") or 0),
        requested_amount_rub=float(app.get("amount") or 1),
        term_months=int(app.get("term_months") or 12),
    )


def _to_verification_data(req: DecideRequest) -> VerificationData:
    """
    К моменту вызова /decide пайплайн уже прошёл completeness-проверку
    (согласия есть) и dataCheck (стоп-факторов нет, сервисы доступны).
    Поэтому consent-флаги = True, статусы check-ов восстанавливаем из
    списка checks в запросе.
    """
    company_status_raw = str(req.company.get("status", "ACTIVE")).upper()
    company_status = "active" if company_status_raw == "ACTIVE" else "inactive"

    fns_status = "ok"
    bki_status = "ok"
    for check in req.checks:
        t = str(check.get("type", "")).upper()
        s = str(check.get("status", "OK")).upper()
        mapped = "ok" if s == "OK" else ("unavailable" if s == "UNAVAILABLE" else "pending")
        if "EGRUL" in t:
            fns_status = mapped
        elif "BKI" in t:
            bki_status = mapped

    return VerificationData(
        company_status=company_status,
        representative_authorized=True,
        personal_data_consent=True,
        credit_history_consent=True,
        fns_check=fns_status,
        bki_check=bki_status,
    )


_RECOMMENDATION_TO_DECISION = {
    "approve": "APPROVE",
    "alternative_offer": "APPROVE_CHANGED_TERMS",
    "reject": "REJECT",
    "manual_review": "MANUAL_REVIEW",
    "needs_info": "REQUEST_INFO",
}


# ── Эндпоинт ──────────────────────────────────────────────────────────────────

@router.post("/decide", response_model=DecideResponse)
def decide(
    payload: DecideRequest,
    scorer=Depends(load_scorer),
    engine: DecisionEngine = Depends(load_engine),
) -> DecideResponse:
    scoring_input = _to_scoring_input(payload)
    scoring = scorer.predict(scoring_input)
    verifications = _to_verification_data(payload)
    decision = engine.evaluate(scoring_input, scoring, verifications)

    final_terms: dict[str, Any] | None = None
    if decision.offer:
        o = decision.offer
        final_terms = {
            "amount": o.amount_rub,
            "term_months": o.term_months,
            "rate": round(o.annual_interest_rate * 100, 2),
            "monthly_payment": o.estimated_monthly_payment_rub,
        }

    top_factors = [
        {
            "name": f.feature,
            "value": f.shap_log_odds,
            "contribution": abs(f.shap_log_odds),
            "direction": "POSITIVE" if f.direction == "decreases_risk" else "NEGATIVE",
            "client_text": f.description,
        }
        for f in scoring.risk_factors
    ]

    score = max(0, min(1000, round((1.0 - scoring.predicted_pd_12m) * 1000)))
    decision_enum = _RECOMMENDATION_TO_DECISION.get(decision.recommendation, "MANUAL_REVIEW")

    return DecideResponse(
        decision_id=str(uuid4()),
        scoring_result_id=str(uuid4()),
        score=score,
        probability_of_default=scoring.predicted_pd_12m,
        risk_class=scoring.risk_level.upper(),
        top_factors=top_factors,
        model_type="CATBOOST",
        model_version=scoring.model_version,
        decision=decision_enum,
        final_terms=final_terms,
        reasons=decision.reason_codes,
        client_reasons=[decision.explanation] if decision.explanation else [],
        missing_data=decision.missing_items,
        policy_version=decision.policy_version,
    )
