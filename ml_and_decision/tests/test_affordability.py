"""Юнит-тесты трёх чистых функций модуля decision.affordability"""

import math
import pytest

from decision.affordability import annuity_payment, max_sustainable_payment, principal_for_payment


# ─── annuity_payment ──────────────────────────────────────────────────────────

class TestAnnuityPayment:
    def test_zero_principal_returns_zero(self):
        assert annuity_payment(0.0, 0.18, 12) == 0.0

    def test_zero_rate_divides_evenly(self):
        # Без процентов платёж = сумма / количество месяцев
        assert annuity_payment(120_000, 0.0, 12) == pytest.approx(10_000.0)

    def test_known_value(self):
        # 180 000 ₽, 18% годовых, 12 мес → ≈ 16 494 ₽/мес
        payment = annuity_payment(180_000, 0.18, 12)
        assert payment == pytest.approx(16_494, rel=1e-3)

    def test_single_month(self):
        # За 1 месяц = principal * (1 + rate/12)
        p, r = 100_000, 0.18
        expected = 100_000 * (1 + r / 12)
        assert annuity_payment(p, r, 1) == pytest.approx(expected, rel=1e-9)

    def test_negative_principal_raises(self):
        with pytest.raises(ValueError):
            annuity_payment(-1, 0.18, 12)

    def test_negative_rate_raises(self):
        with pytest.raises(ValueError):
            annuity_payment(100_000, -0.01, 12)

    def test_zero_months_raises(self):
        with pytest.raises(ValueError):
            annuity_payment(100_000, 0.18, 0)


# ─── principal_for_payment ────────────────────────────────────────────────────

class TestPrincipalForPayment:
    def test_zero_payment_returns_zero(self):
        assert principal_for_payment(0.0, 0.18, 12) == 0.0

    def test_inverse_of_annuity(self):
        # principal_for_payment(annuity_payment(P, r, n), r, n) ≈ P
        p, r, n = 180_000, 0.18, 12
        payment = annuity_payment(p, r, n)
        recovered = principal_for_payment(payment, r, n)
        assert recovered == pytest.approx(p, rel=1e-6)

    def test_zero_rate(self):
        # Без процентов: principal = payment * months
        assert principal_for_payment(10_000, 0.0, 12) == pytest.approx(120_000.0)

    def test_negative_payment_raises(self):
        with pytest.raises(ValueError):
            principal_for_payment(-100, 0.18, 12)

    def test_zero_months_raises(self):
        with pytest.raises(ValueError):
            principal_for_payment(10_000, 0.18, 0)


# ─── max_sustainable_payment ─────────────────────────────────────────────────

class TestMaxSustainablePayment:
    def test_typical_case(self):
        # Выручка 1 200 000 за 6 мес → 200 000/мес; расходы 50 000; долги 0
        # Свободный поток = 150 000; fraction = 0.60 → 90 000
        result = max_sustainable_payment(1_200_000, 50_000, 0.0, 0.60)
        assert result == pytest.approx(90_000.0)

    def test_no_free_cashflow_returns_zero(self):
        # Расходы превышают выручку → результат не может быть отрицательным
        result = max_sustainable_payment(120_000, 25_000, 0.0, 0.60)
        # avg = 20 000, expenses = 25 000 → free < 0 → clamp to 0
        assert result == 0.0

    def test_existing_debt_reduces_capacity(self):
        # С существующим долгом свободный поток меньше
        without_debt = max_sustainable_payment(600_000, 20_000, 0.0, 0.60)
        with_debt    = max_sustainable_payment(600_000, 20_000, 30_000, 0.60)
        assert with_debt < without_debt

    def test_fraction_scales_linearly(self):
        base = max_sustainable_payment(600_000, 20_000, 0.0, 1.0)
        half = max_sustainable_payment(600_000, 20_000, 0.0, 0.5)
        assert half == pytest.approx(base * 0.5)

    def test_full_expenses_returns_zero(self):
        # Расходы = вся выручка → нет свободных средств
        result = max_sustainable_payment(600_000, 100_000, 0.0, 0.60)
        assert result == 0.0
