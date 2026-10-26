"""Demo credit-risk ML package, independent of the host application's ORM/API.

The model is trained solely on SYNTHETIC credit outcomes.
Do not use predictions for real lending decisions.
"""

from .schemas import ScoringInput, ScoringResult

__all__ = ["ScoringInput", "ScoringResult"]
