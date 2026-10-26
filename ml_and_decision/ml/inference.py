"""Production-shaped serving adapter for the SYNTHETIC CatBoost scoring model.

Only use on demonstration data. Decision Engine and workflow statuses are outside
this module; no loan approvals/denials are made here.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd
from catboost import CatBoostClassifier, Pool

from .features import CATEGORY_FEATURES, FEATURE_COLUMNS, build_features
from .schemas import RiskFactor, ScoringInput, ScoringResult


def factor_description(feature: str, values: dict) -> str:
    labels = {
        "industry": f"Отрасль деятельности: {values['industry']}",
        "product_type": f"Тип кредита: {values['product_type']}",
        "company_age_months": f"Срок работы бизнеса: {values['company_age_months']} мес.",
        "avg_monthly_revenue_rub": f"Среднемесячная выручка: {values['avg_monthly_revenue_rub']:,.0f} ₽",
        "mandatory_expense_ratio": f"Доля обязательных расходов в выручке: {values['mandatory_expense_ratio']:.0%}",
        "existing_payment_ratio": f"Доля платежей по действующим кредитам: {values['existing_payment_ratio']:.0%}",
        "free_cashflow_rub": f"Остаток после расходов и платежей по кредитам: {values['free_cashflow_rub']:,.0f} ₽/мес.",
        "has_active_loans": "Есть действующие кредиты" if values["has_active_loans"] else "Нет действующих кредитов",
        "past_overdue_count": f"Ранее зафиксированные просрочки: {values['past_overdue_count']}",
        "requested_amount_rub": f"Запрошено: {values['requested_amount_rub']:,.0f} ₽",
        "term_months": f"Запрошенный срок: {values['term_months']} мес.",
        "requested_payment_principal_proxy_rub": f"Оценка погашения только основного долга: {values['requested_payment_principal_proxy_rub']:,.0f} ₽/мес.",
        "payment_burden_ratio": f"Оценочная долговая нагрузка: {values['payment_burden_ratio']:.0%} от выручки",
        "loan_to_revenue_6m_ratio": f"Сумма кредита / выручка за 6 мес.: {values['loan_to_revenue_6m_ratio']:.2f}",
        "affordability_coverage_proxy": f"Покрытие платежа доступными средствами (условно): {values['affordability_coverage_proxy']:.2f}",
    }
    return labels[feature]


class CreditRiskScorer:
    """Instantiate ONCE at API startup and inject/reuse on all requests."""

    def __init__(self, model_dir: str | Path = "models"):
        folder = Path(model_dir)
        self.metadata = json.loads((folder / "metadata.json").read_text(encoding="utf-8"))
        if self.metadata["feature_columns"] != list(FEATURE_COLUMNS):
            raise ValueError("Feature schema mismatch. Retrain the model with current ml/features.py")
        self.model = CatBoostClassifier()
        self.model.load_model(str(folder / "model.cbm"))
        if list(self.model.feature_names_) != list(FEATURE_COLUMNS):
            raise ValueError("Model columns differ from code. Refusing unsafe prediction")

    def predict(self, payload: ScoringInput | dict, explain: bool = True) -> ScoringResult:
        input_data = payload if isinstance(payload, ScoringInput) else ScoringInput.model_validate(payload)
        values = build_features(input_data)
        x = pd.DataFrame([values], columns=list(FEATURE_COLUMNS))
        pd_12m = float(self.model.predict_proba(x)[0, 1])
        thresholds = self.metadata["risk_thresholds"]
        risk = "low" if pd_12m < thresholds["low_lt"] else (
            "medium" if pd_12m < thresholds["medium_lt"] else "high"
        )
        factors: list[RiskFactor] = []
        if explain:
            pool = Pool(x, cat_features=list(CATEGORY_FEATURES))
            shap_values = self.model.get_feature_importance(pool, type="ShapValues")[0, :-1]
            # Values are on LOG-ODDS scale (not percentages of PD).
            # Select up to four influential features for the requested application.
            ordered = np.argsort(np.abs(shap_values))[::-1]
            for i in ordered[:4]:
                feature_name = FEATURE_COLUMNS[int(i)]
                contribution = float(shap_values[int(i)])
                factors.append(RiskFactor(
                    feature=feature_name,
                    description=factor_description(feature_name, values),
                    direction="increases_risk" if contribution >= 0 else "decreases_risk",
                    shap_log_odds=round(contribution, 5),
                ))
        return ScoringResult(
            application_id=input_data.application_id,
            risk_level=risk,
            predicted_pd_12m=round(pd_12m, 6),
            risk_factors=factors,
            model_version=self.metadata["model_version"],
        )
