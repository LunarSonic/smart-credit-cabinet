import os
from functools import lru_cache

from fastapi import APIRouter, Depends

from decision import DecisionEngine
from decision.schemas import DecisionEvaluation, DecisionRequest
from ml.inference import CreditRiskScorer

from .fastapi_router import load_scorer

router = APIRouter(prefix="/internal/decision", tags=["demo-underwriting"])


@lru_cache(maxsize=1)
def load_engine() -> DecisionEngine:
    return DecisionEngine(policy_path=os.environ.get("DECISION_POLICY_PATH"))


@router.post("/evaluate", response_model=DecisionEvaluation)
def evaluate_application(
    payload: DecisionRequest,
    scorer: CreditRiskScorer = Depends(load_scorer),
    engine: DecisionEngine = Depends(load_engine),
) -> DecisionEvaluation:
    # In production-shaped integration: verify rights and fetch mock check results
    # on the server, instead of accepting 'verifications' directly from a browser.
    scored = scorer.predict(payload.application)
    result = engine.evaluate(payload.application, scored, payload.verifications)
    return DecisionEvaluation(scoring=scored, decision=result)
