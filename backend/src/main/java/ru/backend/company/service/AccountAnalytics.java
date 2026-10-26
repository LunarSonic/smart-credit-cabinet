package ru.backend.company.service;

import ru.backend.company.entity.AccountTurnover;
import ru.backend.company.repository.AccountTurnoverRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Простая аналитика по оборотам счёта: средние, тренд, число месяцев подряд растущих оборотов */

@Component
public class AccountAnalytics {
    private final AccountTurnoverRepository repo;

    public AccountAnalytics(AccountTurnoverRepository repo) { this.repo = repo; }

    public AccountStats stats(UUID companyId) {
        List<AccountTurnover> rows = repo.findTop6ByCompanyIdOrderByMonthDesc(companyId);
        if (rows.isEmpty()) {
            return new AccountStats(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());
        }
        BigDecimal avgTurnover = avg(rows.stream().map(AccountTurnover::getTurnoverAmount).toList(), 2);
        BigDecimal avgBalance = avg(rows.stream().map(AccountTurnover::getAvgBalance).toList(), 2);

        int growing = 0;
        for (int i = 0; i < rows.size() - 1; i++) {
            if (rows.get(i).getTurnoverAmount().compareTo(rows.get(i + 1).getTurnoverAmount()) > 0) growing++;
            else break;
        }

        BigDecimal trend = BigDecimal.ZERO;
        int k = Math.min(3, rows.size() / 2);
        if (k >= 1) {
            BigDecimal last = avg(rows.subList(0, k).stream().map(AccountTurnover::getTurnoverAmount).toList(), 4);
            BigDecimal prev = avg(rows.subList(k, 2 * k).stream().map(AccountTurnover::getTurnoverAmount).toList(), 4);
            if (prev.signum() != 0) trend = last.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP);
        }

        List<Map<String, Object>> monthly = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", rows.get(i).getMonth().toString());
            m.put("turnover", rows.get(i).getTurnoverAmount());
            m.put("avg_balance", rows.get(i).getAvgBalance());
            monthly.add(m);
        }
        return new AccountStats(rows.size(), avgTurnover, avgBalance, trend, growing, monthly);
    }

    private static BigDecimal avg(List<BigDecimal> values, int scale) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), scale, RoundingMode.HALF_UP);
    }
}
