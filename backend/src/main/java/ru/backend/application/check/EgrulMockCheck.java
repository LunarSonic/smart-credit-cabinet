package ru.backend.application.check;

import org.springframework.stereotype.Component;
import ru.backend.application.entity.enums.CheckStatus;
import ru.backend.application.entity.enums.CheckType;
import ru.backend.company.entity.Company;

import java.util.LinkedHashMap;
import java.util.Map;

/** Мок ЕГРЮЛ/ЕГРИП + «Прозрачный бизнес»: компания действует, ОКВЭД и руководитель совпадают */

@Component
public class EgrulMockCheck implements ExternalCheck {

    private final MockEgrulRepository repo;

    public EgrulMockCheck(MockEgrulRepository repo) {
        this.repo = repo;
    }

    @Override
    public CheckType type() {
        return CheckType.EGRUL;
    }

    @Override
    public CheckOutcome run(Company c) {
        Map<String, Object> p = new LinkedHashMap<>();
        var rec = repo.findById(c.getInn());

        if (rec.isEmpty()) {
            p.put("found", false);
            p.put("message", "Организация с таким ИНН не найдена в реестре");
            return new CheckOutcome(CheckStatus.FAILED, p);
        }

        MockEgrulRecord r = rec.get();
        boolean okvedMatches    = r.getOkved().equals(c.getOkved());
        boolean directorMatches = r.getDirectorName().equalsIgnoreCase(c.getDirectorName());

        p.put("found", true);
        p.put("registry_status", r.getStatus());
        p.put("okved", r.getOkved());
        p.put("director_name", r.getDirectorName());
        p.put("registered_at", r.getRegisteredAt().toString());
        p.put("okved_matches", okvedMatches);
        p.put("director_matches", directorMatches);

        if ("LIQUIDATING".equals(r.getStatus())) {
            p.put("stop_factor", "LIQUIDATION");
            p.put("message", "Компания находится в процессе ликвидации");
            return new CheckOutcome(CheckStatus.FAILED, p);
        }

        boolean ok = okvedMatches && directorMatches;
        if (!ok) p.put("message", "Данные ЕГРЮЛ не совпадают с данными компании в банке");
        return new CheckOutcome(ok ? CheckStatus.OK : CheckStatus.FAILED, p);
    }
}
