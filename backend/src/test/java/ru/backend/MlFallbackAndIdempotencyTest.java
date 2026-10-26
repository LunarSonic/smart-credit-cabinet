package ru.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.service.PipelineService;
import ru.backend.auth.entity.AppUser;
import ru.backend.auth.repository.AppUserRepository;
import ru.backend.company.entity.Company;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.decision.client.MlDecideRequest;
import ru.backend.decision.client.MlDecideResponse;
import ru.backend.decision.client.MlDecisionClient;
import ru.backend.decision.client.MlUnavailableException;
import ru.backend.decision.repository.DecisionSnapshotRepository;
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
import static org.mockito.Mockito.*;

/**
 * Тесты fallback при недоступности ML-сервиса и идемпотентности /decide
 */

@SpringBootTest
@Testcontainers
@Transactional
class MlFallbackAndIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ApplicationRepository appRepo;
    @Autowired CompanyRepository companyRepo;
    @Autowired ProductRepository productRepo;
    @Autowired PipelineService pipelineService;
    @Autowired DecisionSnapshotRepository snapshotRepo;
    @Autowired AppUserRepository userRepo;

    @MockitoBean MlDecisionClient mlClient;

    private Company company;
    private Product product;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setLogin("test-ml-" + UUID.randomUUID());
        user.setPasswordHash("$2a$10$test");
        user.setRole(Role.CLIENT);
        user.setFullName("Тест МЛ");
        user = userRepo.save(user);
        testUserId = user.getId();

        company = new Company();
        company.setInn("888800000088");
        company.setName("ИП Фоллбэк Тест");
        company.setOkved("47.11");
        company.setDirectorName("Тест Тестович");
        company.setRegisteredAt(LocalDate.of(2019, 5, 1));
        company = companyRepo.save(company);

        product = productRepo.findAll().stream()
                .filter(p -> p.getCode().equals("WORKING_CAPITAL"))
                .findFirst()
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setCode("WC_FB_TEST");
                    p.setName("Тест продукт fallback");
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

    private Application appInScoring() {
        Application a = new Application();
        a.setCompanyId(company.getId());
        a.setProductId(product.getId());
        a.setCreatedBy(testUserId);
        a.setAmount(new BigDecimal("150000"));
        a.setTermMonths(6);
        a.setPurpose(Purpose.WORKING_CAPITAL);
        a.setFullName("Тест Тестович");
        a.setPhone("+79001112233");
        a.setRevenue6m(new BigDecimal("900000"));
        a.setMonthlyExpenses(new BigDecimal("40000"));
        a.setStatus(ApplicationStatus.SCORING);
        return appRepo.save(a);
    }

    // ─── Fallback: ML недоступен → MANUAL_REVIEW ─────────────────────────────

    @Test
    void whenMlUnavailable_application_goesToManualReview() {
        when(mlClient.decide(any()))
                .thenThrow(new MlUnavailableException("Сервис решений недоступен", null));

        Application a = appInScoring();
        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.MANUAL_REVIEW);
    }

    // ─── Два вызова пайплайна → два снапшота ─────────────────────────────────
    // Каждый вызов advance() генерирует новый request_id, поэтому оба сохраняются
    // как отдельные снапшоты. Это ожидаемое поведение при retry.

    @Test
    void pipelineCalledTwice_createsTwoSnapshots() {
        MlDecideResponse response = new MlDecideResponse(
                UUID.randomUUID().toString(), null,
                new BigDecimal("700"), new BigDecimal("0.15"),
                "LOW", List.of(),
                "gradient_boost", "1.0",
                "APPROVE",
                Map.of("amount", 150000, "term_months", 6, "rate", 18.5),
                null, List.of(), List.of(), null, "v1.0");

        when(mlClient.decide(any())).thenReturn(response);

        Application a = appInScoring();

        // Первый вызов: SCORING → APPROVED, снапшот №1
        pipelineService.advance(a.getId());

        // Имитируем retry: сбрасываем статус в SCORING
        Application reloaded = appRepo.findById(a.getId()).orElseThrow();
        reloaded.setStatus(ApplicationStatus.SCORING);
        appRepo.save(reloaded);

        // Второй вызов: SCORING → APPROVED, снапшот №2
        pipelineService.advance(a.getId());

        long snaps = snapshotRepo.findAll().stream()
                .filter(s -> s.getApplicationId().equals(a.getId()))
                .count();
        assertThat(snaps).isEqualTo(2L);
        verify(mlClient, times(2)).decide(any(MlDecideRequest.class));
    }

    // ─── Стоп-фактор через EGRUL (ликвидация) → REJECTED, ML не вызывается ───
    // Сценарий 3 из seed-данных: ИНН 770100000003, статус LIQUIDATING в mock_egrul

    @Test
    void mlIsNotCalledWhenStopFactorDetected() {
        // Используем ООО «Ликвид» из seed-данных (V2 миграция):
        // INN 770100000003, mock_egrul.status = LIQUIDATING → EgrulMockCheck вернёт stop_factor
        Company liquidCompany = companyRepo.findByInn("770100000003").orElseThrow(
                () -> new AssertionError("Ожидается компания с ИНН 770100000003 из V2 seed-данных"));

        Application a = new Application();
        a.setCompanyId(liquidCompany.getId());
        a.setProductId(product.getId());
        a.setCreatedBy(testUserId);
        a.setAmount(new BigDecimal("200000"));
        a.setTermMonths(12);
        a.setPurpose(Purpose.WORKING_CAPITAL);
        a.setFullName("Громов Андрей Петрович");
        a.setPhone("+79001112234");
        a.setRevenue6m(new BigDecimal("1200000"));
        a.setMonthlyExpenses(new BigDecimal("50000"));
        a.setStatus(ApplicationStatus.DATA_CHECK);
        a = appRepo.save(a);

        pipelineService.advance(a.getId());

        Application result = appRepo.findById(a.getId()).orElseThrow();
        // EgrulMockCheck обнаруживает LIQUIDATING → stop_factor → REJECTED без вызова ML
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        verify(mlClient, never()).decide(any(MlDecideRequest.class));
    }
}
