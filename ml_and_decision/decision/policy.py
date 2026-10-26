
import json
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ProductPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")
    min_amount_rub: float = Field(gt=0)
    max_amount_rub: float = Field(gt=0)
    min_term_months: int = Field(gt=0)
    max_term_months: int = Field(gt=0)
    demo_annual_interest_rate: float = Field(ge=0, lt=1)

    @model_validator(mode="after")
    def check_ranges(self):
        if self.max_amount_rub < self.min_amount_rub:
            raise ValueError("max_amount_rub must be >= min_amount_rub")
        if self.max_term_months < self.min_term_months:
            raise ValueError("max_term_months must be >= min_term_months")
        return self


class DecisionPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")
    version: str = Field(min_length=1)
    disclaimer: str = Field(min_length=1)
    max_new_payment_fraction_of_free_cashflow: float = Field(gt=0, le=1)
    amount_rounding_step_rub: int = Field(gt=0)
    high_risk_action: Literal["manual_review", "reject"] = "manual_review"
    medium_risk_action: Literal["manual_review", "proceed"] = "proceed"
    products: dict[str, ProductPolicy]

    @model_validator(mode="after")
    def product_requirements(self):
        if not {"working_capital", "equipment"}.issubset(self.products):
            raise ValueError("Both MVP credit products must be configured")
        return self


def load_policy(path: str | Path | None = None) -> DecisionPolicy:
    location = Path(path) if path is not None else Path(__file__).with_name("policy.json")
    return DecisionPolicy.model_validate(json.loads(location.read_text(encoding="utf-8")))
