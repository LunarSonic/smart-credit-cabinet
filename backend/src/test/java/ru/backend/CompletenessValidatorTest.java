package ru.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.enums.ConsentType;
import ru.backend.application.service.CompletenessValidator;
import ru.backend.application.service.IssueDraft;
import ru.backend.company.entity.Company;
import ru.backend.product.entity.Product;
import ru.backend.product.entity.enums.Purpose;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тест CompletenessValidator — не требует Spring-контекста или БД
 */

class CompletenessValidatorTest {

    private CompletenessValidator validator;
    private Product product;
    private Company company;

    @BeforeEach
    void setUp() {
        validator = new CompletenessValidator();

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setCode("WORKING_CAPITAL");
        product.setName("Кредит на текущие расходы");
        product.setMinAmount(new BigDecimal("50000"));
        product.setMaxAmount(new BigDecimal("500000"));
        product.setMinTermMonths(3);
        product.setMaxTermMonths(12);
        product.setBaseRate(new BigDecimal("18.50"));

        company = new Company();
        company.setInn("770100000001");
        company.setName("ИП Петрова В.А.");
        company.setOkved("47.11");
        company.setDirectorName("Петрова Виктория Андреевна");
        company.setRegisteredAt(LocalDate.of(2020, 3, 15));
    }

    private Application validApplication() {
        Application a = new Application();
        a.setId(UUID.randomUUID());
        a.setFullName("Петрова Виктория Андреевна");
        a.setPhone("+79001234567");
        a.setRevenue6m(new BigDecimal("1200000"));
        a.setMonthlyExpenses(new BigDecimal("50000"));
        a.setPurpose(Purpose.WORKING_CAPITAL);
        a.setAmount(new BigDecimal("180000"));
        a.setTermMonths(12);
        return a;
    }

    private Set<ConsentType> allConsents() {
        return Set.of(ConsentType.BKI_REQUEST, ConsentType.PERSONAL_DATA);
    }

    // ─── Успешная валидация ────────────────────────────────────────────────────

    @Test
    void noIssues_whenAllFieldsValid() {
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(), allConsents());
        assertThat(issues).isEmpty();
    }

    // ─── Обязательные поля ────────────────────────────────────────────────────

    @Test
    void issue_whenFullNameBlank() {
        Application a = validApplication();
        a.setFullName("  ");
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("fullName"));
    }

    @Test
    void issue_whenPhoneNull() {
        Application a = validApplication();
        a.setPhone(null);
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("phone"));
    }

    @Test
    void issue_whenRevenue6mZero() {
        Application a = validApplication();
        a.setRevenue6m(BigDecimal.ZERO);
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("revenue6m"));
    }

    // ─── Диапазоны продукта ───────────────────────────────────────────────────

    @Test
    void issue_whenAmountBelowMin() {
        Application a = validApplication();
        a.setAmount(new BigDecimal("10000"));  // ниже 50 000
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("amount"))
                .anyMatch(i -> i.message().contains("ниже минимума"));
    }

    @Test
    void issue_whenAmountAboveMax() {
        Application a = validApplication();
        a.setAmount(new BigDecimal("600000"));  // выше 500 000
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("amount"))
                .anyMatch(i -> i.message().contains("выше максимума"));
    }

    @Test
    void issue_whenTermBelowMin() {
        Application a = validApplication();
        a.setTermMonths(1);  // ниже 3 мес
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("termMonths"));
    }

    @Test
    void issue_whenTermAboveMax() {
        Application a = validApplication();
        a.setTermMonths(24);  // выше 12 мес
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("termMonths"));
    }

    // ─── ИНН ──────────────────────────────────────────────────────────────────

    @Test
    void issue_whenInnInvalid() {
        company.setInn("123");  // слишком короткий
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("inn"));
    }

    @Test
    void noIssue_whenInn12digits() {
        company.setInn("770100000001");  // 12 цифр — валидный ИП
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(), allConsents());
        assertThat(issues).noneMatch(i -> i.field().equals("inn"));
    }

    // ─── Согласия ─────────────────────────────────────────────────────────────

    @Test
    void issue_whenBkiConsentMissing() {
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(),
                Set.of(ConsentType.PERSONAL_DATA));   // BKI отсутствует
        assertThat(issues).anyMatch(i -> i.field().equals("consent.bki"));
    }

    @Test
    void issue_whenPersonalDataConsentMissing() {
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(),
                Set.of(ConsentType.BKI_REQUEST));     // PD отсутствует
        assertThat(issues).anyMatch(i -> i.field().equals("consent.personalData"));
    }

    @Test
    void issue_whenBothConsentsMissing() {
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(), Set.of());
        assertThat(issues).anyMatch(i -> i.field().equals("consent.bki"));
        assertThat(issues).anyMatch(i -> i.field().equals("consent.personalData"));
    }

    // ─── Граничные значения ───────────────────────────────────────────────────

    @Test
    void noIssue_whenAmountExactlyAtMin() {
        Application a = validApplication();
        a.setAmount(new BigDecimal("50000"));  // ровно минимум продукта
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).noneMatch(i -> i.field().equals("amount"));
    }

    @Test
    void noIssue_whenAmountExactlyAtMax() {
        Application a = validApplication();
        a.setAmount(new BigDecimal("500000"));  // ровно максимум продукта
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).noneMatch(i -> i.field().equals("amount"));
    }

    @Test
    void issue_whenAmountNull() {
        Application a = validApplication();
        a.setAmount(null);
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).anyMatch(i -> i.field().equals("amount"));
    }

    @Test
    void noIssue_whenTermExactlyAtMin() {
        Application a = validApplication();
        a.setTermMonths(3);  // ровно минимум
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).noneMatch(i -> i.field().equals("termMonths"));
    }

    @Test
    void noIssue_whenTermExactlyAtMax() {
        Application a = validApplication();
        a.setTermMonths(12);  // ровно максимум
        List<IssueDraft> issues = validator.validate(a, product, company, List.of(), allConsents());
        assertThat(issues).noneMatch(i -> i.field().equals("termMonths"));
    }

    @Test
    void noIssue_whenInn10digits() {
        company.setInn("7701000001");  // 10 цифр — валидный для ООО
        List<IssueDraft> issues = validator.validate(
                validApplication(), product, company, List.of(), allConsents());
        assertThat(issues).noneMatch(i -> i.field().equals("inn"));
    }
}
