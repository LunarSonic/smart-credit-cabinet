"""External scoring contract: values originate from forms and mocked FNS/BKI APIs."""

from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


ProductType = Literal["working_capital", "equipment"]
RiskLevel = Literal["low", "medium", "high"]


class ScoringInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    application_id: str | None = None
    application_date: date = Field(description="Snapshot date; no future information is allowed")
    registration_date: date = Field(description="Company registration date, mocked FNS")
    industry: str = Field(min_length=1, description="Industry category, mocked FNS")
    product_type: ProductType
    revenue_6m_rub: float = Field(ge=0, description="Total revenue over six months, RUB")
    monthly_mandatory_expenses_rub: float = Field(ge=0)
    has_active_loans: bool
    existing_monthly_debt_payment_rub: float = Field(ge=0)
    past_overdue_count: int = Field(ge=0, le=500, description="Previous late payments, mocked BKI")
    requested_amount_rub: float = Field(gt=0)
    term_months: int = Field(ge=1, le=120)

    @model_validator(mode="after")
    def validate_snapshots(self):
        if self.registration_date > self.application_date:
            raise ValueError("registration_date cannot be after application_date")
        if not self.has_active_loans and self.existing_monthly_debt_payment_rub > 0:
            raise ValueError("existing payment must be zero when has_active_loans is false")
        return self


class RiskFactor(BaseModel):
    feature: str
    description: str
    direction: Literal["increases_risk", "decreases_risk"]
    shap_log_odds: float


class ScoringResult(BaseModel):
    application_id: str | None = None
    risk_level: RiskLevel
    predicted_pd_12m: float = Field(ge=0, le=1)
    risk_factors: list[RiskFactor]
    model_version: str
    scoring_backend: Literal["catboost"] = "catboost"
    data_origin: Literal["synthetic_training"] = "synthetic_training"
    warning: str = "Educational synthetic-data estimate; not validated for real lending."
