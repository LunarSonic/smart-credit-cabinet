package ru.backend.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.backend.application.check.ExternalCheck;
import ru.backend.application.check.CheckOutcome;
import ru.backend.company.repository.AccountTurnoverRepository;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.ApplicationIssue;
import ru.backend.application.entity.CheckResult;
import ru.backend.application.entity.Consent;
import ru.backend.application.entity.DeclaredObligation;
import ru.backend.application.entity.enums.ActorType;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.entity.enums.CheckStatus;
import ru.backend.application.entity.enums.CheckType;
import ru.backend.application.entity.enums.ConsentType;
import ru.backend.application.entity.enums.IssueSource;
import ru.backend.application.repository.ApplicationIssueRepository;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.repository.CheckResultRepository;
import ru.backend.application.repository.ConsentRepository;
import ru.backend.application.repository.DeclaredObligationRepository;
import ru.backend.company.entity.Company;
import ru.backend.company.repository.CompanyRepository;
import ru.backend.decision.client.MlDecideRequest;
import ru.backend.decision.client.MlDecideResponse;
import ru.backend.decision.entity.DecisionSnapshot;
import ru.backend.decision.entity.enums.Decision;
import ru.backend.decision.repository.DecisionSnapshotRepository;
import ru.backend.offer.service.OfferService;
import ru.backend.product.entity.Product;
import ru.backend.product.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PipelineSteps {

    private static final Logger log = LoggerFactory.getLogger(PipelineSteps.class);
    private final ApplicationRepository apps;
    private final ProductRepository products;
    private final CompanyRepository companies;
    private final DeclaredObligationRepository obligations;
    private final ConsentRepository consents;
    private final ApplicationIssueRepository issues;
    private final CheckResultRepository checks;
    private final DecisionSnapshotRepository snapshots;
    private final ApplicationStateService state;
    private final CompletenessValidator validator;
    private final List<ExternalCheck> externalChecks;
    private final OfferService offerService;
    private final AccountTurnoverRepository turnoverRepo;

    public PipelineSteps(ApplicationRepository apps, ProductRepository products,
                         CompanyRepository companies, DeclaredObligationRepository obligations,
                         ConsentRepository consents, ApplicationIssueRepository issues,
                         CheckResultRepository checks, DecisionSnapshotRepository snapshots,
                         ApplicationStateService state, CompletenessValidator validator,
                         List<ExternalCheck> externalChecks, OfferService offerService,
                         AccountTurnoverRepository turnoverRepo) {
        this.apps = apps; this.products = products; this.companies = companies;
        this.obligations = obligations; this.consents = consents; this.issues = issues;
        this.checks = checks; this.snapshots = snapshots; this.state = state;
        this.validator = validator; this.externalChecks = externalChecks;
        this.offerService = offerService; this.turnoverRepo = turnoverRepo;
    }

    // ─── Шаг 1: проверка полноты (SUBMITTED → COMPLETENESS_CHECK → NEEDS_INFO/DATA_CHECK) ─

    @Transactional
    public void completeness(UUID id) {
        Application a = apps.findById(id).orElseThrow();
        if (a.getStatus() != ApplicationStatus.SUBMITTED) return;

        // Промежуточный статус — фиксируется в истории
        state.transition(a, ApplicationStatus.COMPLETENESS_CHECK, ActorType.SYSTEM, null,
                "Начата проверка полноты данных", null);

        Product p = products.findById(a.getProductId()).orElseThrow();
        Company c = companies.findById(a.getCompanyId()).orElseThrow();
        Set<ConsentType> given = consents.findByApplicationId(id).stream()
                .map(Consent::getType).collect(Collectors.toSet());
        List<IssueDraft> found = validator.validate(a, p, c, obligations.findByApplicationId(id), given);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issues_count", found.size());
        payload.put("issues", found.stream()
                .map(i -> Map.of("field", i.field(), "message", i.message())).toList());
        saveCheck(id, CheckType.COMPLETENESS, found.isEmpty() ? CheckStatus.OK : CheckStatus.FAILED, payload);

        if (found.isEmpty()) {
            state.transition(a, ApplicationStatus.DATA_CHECK, ActorType.SYSTEM, null,
                    "Данные полны", null);
            return;
        }

        for (IssueDraft d : found) {
            ApplicationIssue i = new ApplicationIssue();
            i.setApplicationId(id);
            i.setSource(IssueSource.COMPLETENESS);
            i.setField(d.field());
            i.setMessage(d.message());
            i.setReason(d.reason());
            issues.save(i);
        }
        state.transition(a, ApplicationStatus.NEEDS_INFO, ActorType.SYSTEM, null,
                "Не хватает данных",
                found.stream().map(IssueDraft::message).collect(Collectors.joining("; ")));
    }

    // ─── Шаг 2: проверки из внешних источников (DATA_CHECK → SCORING/MANUAL_REVIEW/REJECTED) ─

    @Transactional
    public void dataCheck(UUID id) {
        Application a = apps.findById(id).orElseThrow();
        if (a.getStatus() != ApplicationStatus.DATA_CHECK) return;
        Company c = companies.findById(a.getCompanyId()).orElseThrow();

        List<String> unavailable = new ArrayList<>();
        String stopFactor = null;

        for (ExternalCheck check : externalChecks) {
            CheckOutcome out = safeRun(check, c);
            saveCheck(id, check.type(), out.status(), out.payload());
            if (out.status() == CheckStatus.UNAVAILABLE) {
                unavailable.add(check.type().name());
            }
            if (out.payload().containsKey("stop_factor")) {
                stopFactor = String.valueOf(out.payload().get("message"));
            }
        }

        if (stopFactor != null) {
            state.transition(a, ApplicationStatus.REJECTED, ActorType.SYSTEM, null,
                    "Стоп-фактор: " + stopFactor, null);
        } else if (!unavailable.isEmpty()) {
            state.transition(a, ApplicationStatus.MANUAL_REVIEW, ActorType.SYSTEM, null,
                    "Сервис(ы) недоступны: " + String.join(", ", unavailable) + " — решение за сотрудником", null);
        } else {
            state.transition(a, ApplicationStatus.SCORING, ActorType.SYSTEM, null,
                    "Все проверки пройдены", null);
        }
    }

    private CheckOutcome safeRun(ExternalCheck check, Company c) {
        try {
            return check.run(c);
        } catch (Exception e) {
            log.warn("Проверка {} упала: {}", check.type(), e.getMessage());
            return new CheckOutcome(CheckStatus.UNAVAILABLE,
                    Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    // ─── Шаг 3: подготовка запроса к ML-сервису ───────────────────────────────

    @Transactional(readOnly = true)
    public MlDecideRequest prepareScoring(UUID id) {
        Application a = apps.findById(id).orElseThrow();
        if (a.getStatus() != ApplicationStatus.SCORING) return null;

        Product p = products.findById(a.getProductId()).orElseThrow();
        Company c = companies.findById(a.getCompanyId()).orElseThrow();
        List<DeclaredObligation> declared = obligations.findByApplicationId(id);

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("code", p.getCode());
        product.put("min_amount", p.getMinAmount());
        product.put("max_amount", p.getMaxAmount());
        product.put("min_term_months", p.getMinTermMonths());
        product.put("max_term_months", p.getMaxTermMonths());
        product.put("base_rate", p.getBaseRate());

        BigDecimal monthly = declared.stream().map(DeclaredObligation::getMonthlyPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> application = new LinkedHashMap<>();
        application.put("amount", a.getAmount());
        application.put("term_months", a.getTermMonths());
        application.put("purpose", a.getPurpose() == null ? null : a.getPurpose().name());
        application.put("revenue_6m", a.getRevenue6m());
        application.put("monthly_expenses", a.getMonthlyExpenses());
        application.put("has_loans", !declared.isEmpty());
        application.put("loans_monthly_payment", monthly);

        // Статус компании берём из последней ЕГРЮЛ-проверки
        Map<String, Object> egrulPayload = latestPayload(id, CheckType.EGRUL);
        Map<String, Object> company = new LinkedHashMap<>();
        company.put("inn", c.getInn());
        company.put("okved", c.getOkved());
        company.put("registration_date", c.getRegisteredAt().toString());
        company.put("status", egrulPayload.getOrDefault("registry_status", "ACTIVE"));

        BigDecimal debt = declared.stream()
                .map(d -> d.getRemainingDebt() == null ? BigDecimal.ZERO : d.getRemainingDebt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> declaredObl = new LinkedHashMap<>();
        declaredObl.put("count", declared.size());
        declaredObl.put("total_monthly_payment", monthly);
        declaredObl.put("total_remaining_debt", debt);

        List<Map<String, Object>> checkList = new ArrayList<>();
        for (CheckType t : CheckType.values()) {
            if (t == CheckType.COMPLETENESS) continue;
            checks.findFirstByApplicationIdAndCheckTypeOrderByCheckedAtDesc(id, t).ifPresent(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", r.getCheckType().name());
                m.put("status", r.getStatus().name());
                m.put("details", r.getPayload());
                checkList.add(m);
            });
        }

        return new MlDecideRequest(id, UUID.randomUUID().toString(), product, application, company,
                latestPayload(id, CheckType.ACCOUNT), latestPayload(id, CheckType.BKI),
                declaredObl, checkList);
    }

    private Map<String, Object> latestPayload(UUID id, CheckType t) {
        return checks.findFirstByApplicationIdAndCheckTypeOrderByCheckedAtDesc(id, t)
                .map(CheckResult::getPayload).orElse(Map.of());
    }

    // ─── Шаг 4: применение решения ML-сервиса ─────────────────────────────────

    @Transactional
    public void applyDecision(UUID id, String requestId, MlDecideResponse r) {
        Application a = apps.findById(id).orElseThrow();
        if (a.getStatus() != ApplicationStatus.SCORING) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("decision_id", r.decisionId());
        payload.put("scoring_result_id", r.scoringResultId());
        payload.put("score", r.score());
        payload.put("probability_of_default", r.probabilityOfDefault());
        payload.put("risk_class", r.riskClass());
        payload.put("decision", r.decision());
        payload.put("final_terms", r.finalTerms());
        payload.put("changes", r.changes());
        payload.put("top_factors", r.topFactors());
        payload.put("reasons", r.reasons());
        payload.put("client_reasons", r.clientReasons());
        payload.put("missing_data", r.missingData());
        payload.put("model_type", r.modelType());
        payload.put("model_version", r.modelVersion());
        DecisionSnapshot snap = new DecisionSnapshot();
        snap.setApplicationId(id);
        snap.setDecisionId(r.decisionId());
        snap.setRequestId(requestId);
        snap.setPayload(payload);
        snapshots.save(snap);
        a.setCurrentDecisionRef(r.decisionId());

        Decision d;
        try {
            d = Decision.valueOf(r.decision());
        } catch (IllegalArgumentException e) {
            state.transition(a, ApplicationStatus.MANUAL_REVIEW, ActorType.SYSTEM, null,
                    "Сервис решений вернул неизвестное решение «" + r.decision() + "»", null);
            return;
        }

        switch (d) {
            case APPROVE -> {
                Terms t = terms(a, r);
                offerService.create(a.getId(), t.amount(), t.term(), t.rate(), null);
                state.transition(a, ApplicationStatus.APPROVED, ActorType.SYSTEM, null,
                        "Заявка одобрена", null);
            }
            case APPROVE_CHANGED_TERMS -> {
                Terms t = terms(a, r);
                Map<String, Object> changes = r.changes() != null
                        ? new LinkedHashMap<>(r.changes())
                        : new LinkedHashMap<>();
                if (changes.isEmpty()) {
                    if (t.amount().compareTo(a.getAmount()) != 0)
                        changes.put("amount", Map.of("from", a.getAmount(), "to", t.amount()));
                    if (t.term() != a.getTermMonths())
                        changes.put("term_months", Map.of("from", a.getTermMonths(), "to", t.term()));
                }
                if (!reasonMessages(r).isEmpty())
                    changes.put("reasons", reasonMessages(r));
                offerService.create(a.getId(), t.amount(), t.term(), t.rate(), changes);
                state.transition(a, ApplicationStatus.APPROVED, ActorType.SYSTEM, null,
                        "Заявка одобрена на изменённых условиях", null);
            }
            case REJECT ->
                    state.transition(a, ApplicationStatus.REJECTED, ActorType.SYSTEM, null,
                            "В кредите отказано", null);
            case MANUAL_REVIEW ->
                    state.transition(a, ApplicationStatus.MANUAL_REVIEW, ActorType.SYSTEM, null,
                            "Заявка направлена на проверку специалистом", null);
            case REQUEST_INFO -> {
                List<String> missing = r.missingData() == null ? List.of() : r.missingData();
                List<String> clientRs = r.clientReasons() == null ? List.of() : r.clientReasons();
                if (missing.isEmpty() && clientRs.isEmpty())
                    saveScoringIssue(id, "general", "Для оценки не хватает данных", null);
                for (String field : missing)
                    saveScoringIssue(id, field, missingFieldDescription(field), null);
                for (String reason : clientRs)
                    saveScoringIssue(id, "general", reason, null);
                state.transition(a, ApplicationStatus.NEEDS_INFO, ActorType.SYSTEM, null,
                        "Требуется уточнение данных", null);
            }
        }
    }

    @Transactional
    public void scoringFailed(UUID id, String message) {
        Application a = apps.findById(id).orElseThrow();
        if (a.getStatus() != ApplicationStatus.SCORING) return;
        state.transition(a, ApplicationStatus.MANUAL_REVIEW, ActorType.SYSTEM, null,
                "Сервис решений недоступен", message);
    }

    // ─── вспомогательные ──────────────────────────────────────────────────────

    private static String missingFieldDescription(String field) {
        return switch (field) {
            // поля формы заявки
            case "revenue_6m"                  -> "Укажите выручку за последние 6 месяцев";
            case "monthly_expenses"            -> "Укажите обязательные ежемесячные расходы";
            case "phone"                       -> "Укажите контактный телефон";
            case "full_name"                   -> "Укажите ФИО представителя";
            case "purpose"                     -> "Укажите цель кредита";
            // результаты внешних проверок (возвращает Decision Engine)
            case "fns_check"                   -> "Данные ЕГРЮЛ/ЕГРИП требуют уточнения — обратитесь в банк";
            case "bki_check"                   -> "Кредитная история не подтверждена — обратитесь в банк";
            case "company_status"              -> "Статус компании не удалось подтвердить по данным реестра";
            case "representative_authorized"   -> "Не подтверждены полномочия представителя компании";
            default                            -> "Требуется уточнение по полю: " + field;
        };
    }

    private void saveScoringIssue(UUID id, String field, String message, String reason) {
        ApplicationIssue i = new ApplicationIssue();
        i.setApplicationId(id);
        i.setSource(IssueSource.SCORING);
        i.setField(field);
        i.setMessage(message);
        i.setReason(reason != null ? reason : "Нужно для оценки риска");
        issues.save(i);
    }

    private void saveCheck(UUID id, CheckType type, CheckStatus status, Map<String, Object> payload) {
        CheckResult cr = new CheckResult();
        cr.setApplicationId(id);
        cr.setCheckType(type);
        cr.setStatus(status);
        cr.setPayload(payload);
        checks.save(cr);
    }

    private record Terms(BigDecimal amount, int term, BigDecimal rate) {}

    private Terms terms(Application a, MlDecideResponse r) {
        Map<String, Object> t = r.finalTerms() == null ? Map.of() : r.finalTerms();
        Product p = products.findById(a.getProductId()).orElseThrow();
        BigDecimal amount = num(t.get("amount"), a.getAmount());
        int term = num(t.get("term_months"), BigDecimal.valueOf(a.getTermMonths())).intValue();
        BigDecimal rate = num(t.get("rate"), p.getBaseRate()).setScale(2, RoundingMode.HALF_UP);
        return new Terms(amount, term, rate);
    }

    private static BigDecimal num(Object o, BigDecimal def) {
        return o instanceof Number n ? new BigDecimal(n.toString()) : def;
    }

    private static String str(Object o, String def) {
        return o == null || o.toString().isBlank() ? def : o.toString();
    }

    private static List<String> reasonMessages(MlDecideResponse r) {
        return r.reasons() == null ? List.of() : r.reasons();
    }
}
