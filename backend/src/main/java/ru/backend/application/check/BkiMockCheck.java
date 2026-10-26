package ru.backend.application.check;

import org.springframework.stereotype.Component;
import ru.backend.application.entity.enums.CheckStatus;
import ru.backend.application.entity.enums.CheckType;
import ru.backend.company.entity.Company;

import java.util.LinkedHashMap;
import java.util.Map;

/** Мок БКИ: просрочки и долговая нагрузка. available=false имитирует недоступность сервиса */

@Component
public class BkiMockCheck implements ExternalCheck {

    private final MockBkiRepository repo;

    public BkiMockCheck(MockBkiRepository repo) {
        this.repo = repo;
    }

    @Override
    public CheckType type() {
        return CheckType.BKI;
    }

    @Override
    public CheckOutcome run(Company c) {
        Map<String, Object> p = new LinkedHashMap<>();
        var rec = repo.findById(c.getInn());

        if (rec.isEmpty()) {
            // Кредитная история не найдена — не ошибка, просто нет данных
            p.put("available", true);
            p.put("current_overdue", false);
            p.put("overdue_in_history", false);
            p.put("overdue_count", 0);
            p.put("max_overdue_days", 0);
            p.put("active_loans", 0);
            p.put("total_debt", 0);
            p.put("monthly_payment", 0);
            p.put("note", "Кредитная история не найдена");
            return new CheckOutcome(CheckStatus.OK, p);
        }

        var r = rec.get();
        if (!r.isAvailable()) {
            p.put("available", false);
            p.put("error", "Сервис БКИ не отвечает");
            return new CheckOutcome(CheckStatus.UNAVAILABLE, p);
        }

        p.put("available", true);
        p.put("current_overdue", r.isCurrentOverdue());
        p.put("overdue_in_history", r.isHasOverdue());
        p.put("overdue_count", r.getOverdueCount());
        p.put("max_overdue_days", r.getMaxOverdueDays());
        p.put("active_loans", r.getActiveLoans());
        p.put("total_debt", r.getTotalDebt());
        p.put("monthly_payment", r.getMonthlyPayment());
        return new CheckOutcome(CheckStatus.OK, p);
    }
}
