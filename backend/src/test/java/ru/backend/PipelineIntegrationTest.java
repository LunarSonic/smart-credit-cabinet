package ru.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.Consent;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.entity.enums.ConsentType;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.repository.ConsentRepository;
import ru.backend.application.service.ApplicationStateService;
import ru.backend.application.service.PipelineService;
import ru.backend.offer.repository.OfferRepository;
import ru.backend.auth.entity.AppUser;
import ru.backend.auth.repository.AppUserRepository;
import ru.backend.company.entity.Company;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.decision.client.MlDecideResponse;
import ru.backend.decision.client.MlDecisionClient;
import ru.backend.product.entity.Product;
import ru.backend.product.entity.enums.Purpose;
import ru.backend.product.repository.ProductRepository;
import ru.backend.session.Role;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Интеграционный тест конвейера с реальной PostgreSQL (Testcontainers)
 * ML-сервис подменяется моком
 */

@SpringBootTest
@Testcontainers
@Transactional
class PipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ApplicationRepository appRepo;
    @Autowired CompanyRepository companyRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ConsentRepository consentRepo;
    @Autowired ApplicationStateService stateService;
    @Autowired PipelineService pipelineService;
    @Autowired AppUserRepository userRepo;
    @Autowired OfferRepository offerRepo;

    @MockitoBean MlDecisionClient mlClient;

    private Company company;
    private Product product;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setLogin("test-pipeline-" + UUID.randomUUID());
        user.setPasswordHash("$2a$10$test");
        user.setRole(Role.CLIENT);
        user.setFullName("Тест Пайплайн");
        user = userRepo.save(user);
        testUserId = user.getId();

        company = new Company();
        company.setInn("999900000099");
        company.setName("ИП Тестовый");
        company.setOkved("47.11");
        company.setDirectorName("Тестовый Тест Тестович");
        company.setRegisteredAt(LocalDate.of(2020, 1, 1));
        company = companyRepo.save(company);

        product = productRepo.findAll().stream()
                .filter(p -> p.getCode().equals("WORKING_CAPITAL"))
                .findFirst()
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setCode("WORKING_CAPITAL_TEST");
                    p.setName("Тестовый продукт");
                    p.setSuitablePurpose(Purpose.WORKING_CAPITAL);
                    p.setMinAmount(new BigDecimal("50000"));
                    p.setMaxAmount(new BigDecimal("500000"));
                    p.setMinTermMonths(3);
                    p.setMaxTermMonths(12);
                    p.setBaseRate(new BigDecimal("18.50"));
                    p.setActive(true);
                    return productRepo.save(p);
                });
    }

    private Application createSubmittedApp() {
        Application a = new Application();
        a.setCompanyId(company.getId());
        a.setProductId(product.getId());
        a.setCreatedBy(testUserId);
        a.setAmount(new BigDecimal("180000"));
        a.setTermMonths(12);
        a.setPurpose(Purpose.WORKING_CAPITAL);
        a.setFullName("Тестовый Тест Тестович");
        a.setPhone("+79001234567");
        a.setRevenue6m(new BigDecimal("1500000"));
        a.setMonthlyExpenses(new BigDecimal("80000"));
        a.setStatus(ApplicationStatus.SUBMITTED);
        a = appRepo.save(a);
        addConsents(a.getId());
        return a;
    }

    private void addConsents(UUID appId) {
        for (ConsentType type : ConsentType.values()) {
            Consent c = new Consent();
            c.setApplicationId(appId);
            c.setType(type);
            c.setTextVersion("v1");
            c.setGivenBy(testUserId);
            consentRepo.save(c);
        }
    }

    // ─── Сценарий: нет данных → NEEDS_INFO ───────────────────────────────────

    @Test
    void pipeline_goesTo_needsInfo_whenFieldsMissing() {
        Application a = new Application();
        a.setCompanyId(company.getId());
        a.setProductId(product.getId());
        a.setCreatedBy(testUserId);
        a.setAmount(new BigDecimal("180000"));
        a.setTermMonths(12);
        a.setPurpose(Purpose.WORKING_CAPITAL);
        // fullName, phone, revenue6m — не заполнены
        a.setStatus(ApplicationStatus.SUBMITTED);
        a = appRepo.save(a);

        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.NEEDS_INFO);
    }

    // ─── Сценарий: автоодобрение ──────────────────────────────────────────────

    @Test
    void pipeline_goesTo_approved_onAutoApprove() {
        MlDecideResponse mockResponse = new MlDecideResponse(
                UUID.randomUUID().toString(), null,
                new BigDecimal("720"), new BigDecimal("0.12"),
                "LOW", List.of(),
                "gradient_boost", "1.0",
                "APPROVE",
                Map.of("amount", 180000, "term_months", 12, "rate", 18.5),
                null, List.of(), List.of(), null, "v1.0");
        when(mlClient.decide(any())).thenReturn(mockResponse);

        Application a = createSubmittedApp();
        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    // ─── Сценарий: автоотказ ──────────────────────────────────────────────────

    @Test
    void pipeline_goesTo_rejected_onAutoReject() {
        MlDecideResponse mockResponse = new MlDecideResponse(
                UUID.randomUUID().toString(), null,
                new BigDecimal("200"), new BigDecimal("0.82"),
                "HIGH", List.of(),
                "gradient_boost", "1.0",
                "REJECT",
                null, null,
                List.of("Высокая долговая нагрузка"), List.of("Высокий уровень кредитного риска"),
                null, "v1.0");
        when(mlClient.decide(any())).thenReturn(mockResponse);

        Application a = createSubmittedApp();
        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    // ─── Сценарий: ручное рассмотрение ───────────────────────────────────────

    @Test
    void pipeline_goesTo_manualReview_onBorderlineRisk() {
        MlDecideResponse mockResponse = new MlDecideResponse(
                UUID.randomUUID().toString(), null,
                new BigDecimal("480"), new BigDecimal("0.45"),
                "MEDIUM", List.of(),
                "gradient_boost", "1.0",
                "MANUAL_REVIEW",
                null, null,
                List.of("Пограничный уровень риска"), List.of(),
                null, "v1.0");
        when(mlClient.decide(any())).thenReturn(mockResponse);

        Application a = createSubmittedApp();
        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.MANUAL_REVIEW);
    }

    // ─── Сценарий: ML предлагает изменённые условия ───────────────────────────

    @Test
    void pipeline_goesTo_approved_onApproveChangedTerms() {
        MlDecideResponse mockResponse = new MlDecideResponse(
                UUID.randomUUID().toString(), null,
                new BigDecimal("620"), new BigDecimal("0.22"),
                "LOW", List.of(),
                "gradient_boost", "1.0",
                "APPROVE_CHANGED_TERMS",
                Map.of("amount", 120000, "term_months", 12, "rate", 19.5),
                Map.of("amount", Map.of("from", 180000, "to", 120000)),
                List.of("Сумма снижена по результатам оценки"), List.of(),
                null, "v1.0");
        when(mlClient.decide(any())).thenReturn(mockResponse);

        Application a = createSubmittedApp();
        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        // Оферта должна быть создана с изменёнными условиями
        assertThat(offerRepo.findByApplicationIdOrderByCreatedAtDesc(result.getId())).isNotEmpty();
    }

    // ─── Сценарий: стоп-фактор (ликвидация) → REJECTED без вызова ML ─────────

    @Test
    void pipeline_rejected_when_stopFactor_detected() {
        // ООО «Ликвид» из seed-данных: INN 770100000003, mock_egrul.status = LIQUIDATING
        Company liquidCompany = companyRepo.findByInn("770100000003").orElseThrow(
                () -> new AssertionError("Ожидается компания с ИНН 770100000003 из V2 seed-данных"));

        Application a = new Application();
        a.setCompanyId(liquidCompany.getId());
        a.setProductId(product.getId());
        a.setCreatedBy(testUserId);
        a.setAmount(new BigDecimal("180000"));
        a.setTermMonths(12);
        a.setPurpose(Purpose.WORKING_CAPITAL);
        a.setFullName("Громов Андрей Петрович");
        a.setPhone("+79001234568");
        a.setRevenue6m(new BigDecimal("1500000"));
        a.setMonthlyExpenses(new BigDecimal("80000"));
        a.setStatus(ApplicationStatus.SUBMITTED);
        a = appRepo.save(a);
        addConsents(a.getId());

        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        // ML не должен вызываться при наличии стоп-фактора
        org.mockito.Mockito.verify(mlClient, org.mockito.Mockito.never())
                .decide(org.mockito.ArgumentMatchers.any());
    }

    // ─── Сценарий: при одобрении создаётся оферта ────────────────────────────

    @Test
    void pipeline_createsOffer_when_approved() {
        MlDecideResponse mockResponse = new MlDecideResponse(
                UUID.randomUUID().toString(), null,
                new BigDecimal("720"), new BigDecimal("0.12"),
                "LOW", List.of(),
                "gradient_boost", "1.0",
                "APPROVE",
                Map.of("amount", 180000, "term_months", 12, "rate", 18.5),
                null, List.of(), List.of(), null, "v1.0");
        when(mlClient.decide(any())).thenReturn(mockResponse);

        Application a = createSubmittedApp();
        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

        var offers = offerRepo.findByApplicationIdOrderByCreatedAtDesc(result.getId());
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("180000"));
    }
}
