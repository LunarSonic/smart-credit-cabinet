package ru.backend.application.check;

import org.springframework.stereotype.Component;
import ru.backend.application.entity.enums.CheckStatus;
import ru.backend.application.entity.enums.CheckType;
import ru.backend.company.entity.Company;
import ru.backend.company.service.AccountAnalytics;
import ru.backend.company.service.AccountStats;

import java.util.LinkedHashMap;
import java.util.Map;

/** Внутренние данные банка: обороты и остатки по счёту за последние 6 месяцев */

@Component
public class AccountCheck implements ExternalCheck {

    private final AccountAnalytics analytics;

    public AccountCheck(AccountAnalytics analytics) {
        this.analytics = analytics;
    }

    @Override
    public CheckType type() {
        return CheckType.ACCOUNT;
    }

    @Override
    public CheckOutcome run(Company c) {
        AccountStats s = analytics.stats(c.getId());
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("months", s.months());
        p.put("avg_monthly_turnover", s.avgMonthlyTurnover());
        p.put("avg_balance", s.avgBalance());
        p.put("trend", s.trend());
        p.put("growing_months", s.growingMonths());
        p.put("monthly", s.monthly());
        return new CheckOutcome(s.months() == 0 ? CheckStatus.FAILED : CheckStatus.OK, p);
    }
}
