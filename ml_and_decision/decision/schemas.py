"""Stable contracts between the bank mock adapters, scoring, decision and workflow.

ScoringInput and ScoringResult are imported from ml.schemas on purpose: this
module does not duplicate or alter the ML contract.
"""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from ml.schemas import ScoringInput, ScoringResult

Recommendation = Literal["approve", "alternative_offer", "needs_info", "manual_review", "reject"]


class VerificationData(BaseModel):
    """Results prepared by trusted backend adapters, never trusted from a client form.

    `unknown` means unverified. `unavailable` means the simulated integration
    failed; don't make an automatic lending decision on that basis.
    """

    model_config = ConfigDict(extra="forbid")
    company_status: Literal["active", "inactive", "unknown"] = "unknown"
    representative_authorized: bool | None = None
    personal_data_consent: bool = False
    credit_history_consent: bool = False
    fns_check: Literal["ok", "pending", "unavailable"] = "pending"
    bki_check: Literal["ok", "pending", "unavailable"] = "pending"


class DecisionRequest(BaseModel):
    """Internal API input. Public client must NOT submit risk/model results."""

    model_config = ConfigDict(extra="forbid")
    application: ScoringInput
    verifications: VerificationData


class CreditOffer(BaseModel):
    model_config = ConfigDict(extra="forbid")
    amount_rub: float = Field(gt=0)
    term_months: int = Field(gt=0)
    annual_interest_rate: float = Field(ge=0, lt=1)
    estimated_monthly_payment_rub: float = Field(ge=0)
    demo_only: Literal[True] = True


class DecisionResult(BaseModel):
    """Advisory result: a staff member must confirm the final decision."""

    model_config = ConfigDict(extra="forbid")
    application_id: str | None = None
    recommendation: Recommendation
    reason_codes: list[str]
    explanation: str
    offer: CreditOffer | None = None
    missing_items: list[str] = Field(default_factory=list)
    maximum_new_payment_rub: float | None = Field(default=None, ge=0)
    requires_employee_confirmation: Literal[True] = True
    finalized: Literal[False] = False
    policy_version: str
    scoring_model_version: str
    warning: str = (
        "Synthetic ML risk and fictitious demo lending policy. "
        "A bank employee must review/confirm; no application status was changed."
    )

    @model_validator(mode="after")
    def offer_matches_recommendation(self):
        if (self.offer is not None) != (self.recommendation in {"approve", "alternative_offer"}):
            raise ValueError("offer is required exactly for approve or alternative_offer")
        if self.recommendation != "needs_info" and self.missing_items:
            raise ValueError("missing_items only apply to needs_info")
        return self


class DecisionEvaluation(BaseModel):
    """Output of a single backend operation: score + preliminary decision."""

    model_config = ConfigDict(extra="forbid")
    scoring: ScoringResult
    decision: DecisionResult


class StaffActionInput(BaseModel):
    """Requested bank-employee action, authorized by upstream backend/RBAC."""

    model_config = ConfigDict(extra="forbid")
    staff_id: str = Field(min_length=1)
    action: Literal["approve", "alternative_offer", "reject", "request_info"]
    reason: str | None = None
    offered_amount_rub: float | None = Field(default=None, gt=0)
    offered_term_months: int | None = Field(default=None, gt=0)

    @model_validator(mode="after")
    def check_action_fields(self):
        if self.action == "alternative_offer":
            if self.offered_amount_rub is None or self.offered_term_months is None:
                raise ValueError("Alternative offer requires offered_amount_rub and offered_term_months")
        elif self.offered_amount_rub is not None or self.offered_term_months is not None:
            raise ValueError("Only alternative_offer accepts custom amount/term")
        if self.action in {"reject", "alternative_offer", "request_info"} and not (self.reason or "").strip():
            raise ValueError(f"{self.action} requires a nonempty reason")
        return self


class ValidatedStaffAction(BaseModel):
    """Validated *intent*, not a database commit or actual loan issuance."""

    model_config = ConfigDict(extra="forbid")
    application_id: str | None
    staff_id: str
    action: Literal["approve", "alternative_offer", "reject", "request_info"]
    reason: str | None
    offer: CreditOffer | None = None
    suggested_workflow_status: Literal["proposed_terms", "needs_clarification", "rejected"]
    policy_version: str
    validated_only: Literal[True] = True
    persistence_required: Literal[True] = True
