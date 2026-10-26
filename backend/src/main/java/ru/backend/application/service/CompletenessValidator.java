package ru.backend.application.service;

import org.springframework.stereotype.Component;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.Consent;
import ru.backend.application.entity.DeclaredObligation;
import ru.backend.application.entity.enums.ConsentType;
import ru.backend.company.entity.Company;
import ru.backend.product.entity.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Проверка полноты и корректности данных заявки перед отправкой в конвейер
 * Этап 1 конвейера — COMPLETENESS_CHECK

 * Правила:
 * - обязательные поля заявки и компании должны быть заполнены
 * - сумма и срок — в диапазоне продукта (сообщаем, какой именно параметр не подходит)
 * - ИНН: 10 цифр (юрлицо) или 12 цифр (ИП)
 */

@Component
public class CompletenessValidator {

    public List<IssueDraft> validate(Application a, Product p, Company c,
                                     List<DeclaredObligation> obligations,
                                     Set<ConsentType> consents) {
        List<IssueDraft> issues = new ArrayList<>();

        // ─── личные данные ─────────────────────────────────────────────────────
        if (blank(a.getFullName())) {
            issues.add(new IssueDraft("fullName", "Укажите ФИО представителя",
                    "Необходимо для оформления договора"));
        }
        if (blank(a.getPhone())) {
            issues.add(new IssueDraft("phone", "Укажите контактный телефон",
                    "Потребуется для связи в процессе рассмотрения"));
        }

        // ─── финансовые показатели ─────────────────────────────────────────────
        if (a.getRevenue6m() == null || a.getRevenue6m().compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(new IssueDraft("revenue6m",
                    "Укажите выручку за последние 6 месяцев",
                    "Используется для оценки платёжеспособности"));
        }
        if (a.getMonthlyExpenses() == null || a.getMonthlyExpenses().compareTo(BigDecimal.ZERO) < 0) {
            issues.add(new IssueDraft("monthlyExpenses",
                    "Укажите обязательные ежемесячные расходы",
                    "Нужны для расчёта долговой нагрузки"));
        }

        // ─── параметры кредита ─────────────────────────────────────────────────
        if (a.getPurpose() == null) {
            issues.add(new IssueDraft("purpose", "Укажите цель кредита",
                    "Определяет подходящий продукт"));
        }
        if (a.getAmount() == null || a.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(new IssueDraft("amount", "Укажите запрашиваемую сумму",
                    "Обязательное поле заявки"));
        } else {
            if (a.getAmount().compareTo(p.getMinAmount()) < 0) {
                issues.add(new IssueDraft("amount",
                        "Сумма ниже минимума по продукту «" + p.getName() + "» (" + p.getMinAmount() + " ₽)",
                        "Минимальная сумма по данному продукту — " + p.getMinAmount() + " ₽"));
            } else if (a.getAmount().compareTo(p.getMaxAmount()) > 0) {
                issues.add(new IssueDraft("amount",
                        "Сумма выше максимума по продукту «" + p.getName() + "» (" + p.getMaxAmount() + " ₽)",
                        "Максимальная сумма по данному продукту — " + p.getMaxAmount() + " ₽"));
            }
        }
        if (a.getTermMonths() == null || a.getTermMonths() <= 0) {
            issues.add(new IssueDraft("termMonths", "Укажите срок кредита в месяцах",
                    "Обязательное поле заявки"));
        } else {
            if (a.getTermMonths() < p.getMinTermMonths()) {
                issues.add(new IssueDraft("termMonths",
                        "Срок меньше минимального по продукту «" + p.getName() + "» (" + p.getMinTermMonths() + " мес.)",
                        "Минимальный срок по данному продукту — " + p.getMinTermMonths() + " мес."));
            } else if (a.getTermMonths() > p.getMaxTermMonths()) {
                issues.add(new IssueDraft("termMonths",
                        "Срок больше максимального по продукту «" + p.getName() + "» (" + p.getMaxTermMonths() + " мес.)",
                        "Максимальный срок по данному продукту — " + p.getMaxTermMonths() + " мес."));
            }
        }

        // ─── ИНН компании ──────────────────────────────────────────────────────
        if (!isValidInn(c.getInn())) {
            issues.add(new IssueDraft("inn",
                    "Некорректный формат ИНН (должен содержать 10 цифр для юрлица или 12 цифр для ИП)",
                    "ИНН нужен для запроса в ЕГРЮЛ/ЕГРИП"));
        }

        // ─── согласия ──────────────────────────────────────────────────────────
        if (!consents.contains(ConsentType.BKI_REQUEST)) {
            issues.add(new IssueDraft("consent.bki",
                    "Необходимо согласие на запрос кредитной истории в БКИ",
                    "Без этого согласия рассмотрение заявки невозможно"));
        }
        if (!consents.contains(ConsentType.PERSONAL_DATA)) {
            issues.add(new IssueDraft("consent.personalData",
                    "Необходимо согласие на обработку персональных данных",
                    "Обязательное условие оказания услуги"));
        }

        return issues;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** ИНН: 10 цифр (ЮЛ) или 12 цифр (ИП) */
    private static boolean isValidInn(String inn) {
        if (inn == null) return false;
        return inn.matches("\\d{10}") || inn.matches("\\d{12}");
    }
}
