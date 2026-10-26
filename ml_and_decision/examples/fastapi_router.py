"""Example integration into an existing FastAPI backend.

    from examples.fastapi_router import router
    app.include_router(router)

Mount this router behind existing INTERNAL authentication/authorization controls.
Do NOT expose to the public internet without authentication.
"""

import os
from functools import lru_cache

from fastapi import APIRouter, Depends

from ml.inference import CreditRiskScorer
from ml.schemas import ScoringInput, ScoringResult

router = APIRouter(prefix="/internal/scoring", tags=["demo-ml-scoring"])


@lru_cache(maxsize=1)
def load_scorer() -> CreditRiskScorer:
    return CreditRiskScorer(os.environ.get("SCORING_MODEL_DIR", "models"))


@router.post("/predict", response_model=ScoringResult)
def score_application(payload: ScoringInput, scorer: CreditRiskScorer = Depends(load_scorer)):
    # Caller must verify permissions to read the application/company.
    # Only backend reads mock BKI/FNS and constructs this payload.
    return scorer.predict(payload)
