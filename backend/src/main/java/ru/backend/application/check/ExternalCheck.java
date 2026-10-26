package ru.backend.application.check;

import ru.backend.application.entity.enums.CheckType;
import ru.backend.company.entity.Company;

/** Контракт внешней проверки. В prod-реализации ходила бы в ЕГРЮЛ/БКИ/банк */

public interface ExternalCheck {
    CheckType type();
    CheckOutcome run(Company company);
}
