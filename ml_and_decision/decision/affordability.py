import math


def annuity_payment(principal: float, annual_rate: float, months: int) -> float:
    if not (math.isfinite(principal) and math.isfinite(annual_rate)) or principal < 0:
        raise ValueError("Principal and annual_rate must be finite; principal >= 0")
    if annual_rate < 0 or months < 1:
        raise ValueError("Rate must be >= 0 and months >= 1")
    if principal == 0:
        return 0.0
    r = annual_rate / 12.0
    if r == 0:
        return principal / months
    return principal * r / (-math.expm1(-months * math.log1p(r)))


def principal_for_payment(monthly_payment: float, annual_rate: float, months: int) -> float:
    if not (math.isfinite(monthly_payment) and math.isfinite(annual_rate)):
        raise ValueError("Inputs must be finite")
    if monthly_payment < 0 or annual_rate < 0 or months < 1:
        raise ValueError("Invalid payment/rate/months")
    if monthly_payment == 0:
        return 0.0
    r = annual_rate / 12.0
    if r == 0:
        return monthly_payment * months
    return monthly_payment * (-math.expm1(-months * math.log1p(r))) / r


def max_sustainable_payment(revenue_6m: float, expenses_month: float,
                            debt_payment_month: float, cashflow_fraction: float) -> float:
    avg_monthly_revenue = revenue_6m / 6.0
    free_cashflow = avg_monthly_revenue - expenses_month - debt_payment_month
    return max(0.0, free_cashflow) * cashflow_fraction
