package ru.backend.bank.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.backend.application.dto.ApplicationSummaryDto;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.ApplicationIssue;
import ru.backend.application.entity.enums.ActorType;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.entity.enums.IssueSource;
import ru.backend.application.repository.ApplicationIssueRepository;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.service.ApplicationQueryService;
import ru.backend.application.service.ApplicationStateService;
import ru.backend.bank.dto.BankApplicationDetailDto;
import ru.backend.bank.dto.ManualDecisionRequest;
import ru.backend.bank.entity.ManualDecision;
import ru.backend.bank.entity.enums.ManualAction;
import ru.backend.bank.repository.ManualDecisionRepository;
import ru.backend.common.BusinessException;
import ru.backend.common.NotFoundException;
import ru.backend.decision.entity.DecisionSnapshot;
import ru.backend.decision.repository.DecisionSnapshotRepository;
import ru.backend.offer.service.OfferService;
import ru.backend.product.entity.Product;
import ru.backend.product.repository.ProductRepository;
import ru.backend.session.AuthUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Рабочее место банка: очередь заявок, карточка, ручное решение. */
@Service
public class BankService {

    private final ApplicationRepository apps;
    private final ProductRepository products;
    private final ApplicationIssueRepository issues;
    private final ManualDecisionRepository manualDecisions;
    private final DecisionSnapshotRepository snapshots;
    private final ApplicationStateService state;
    private final ApplicationQueryService query;
    private final OfferService offerService;

    public BankService(ApplicationRepository apps, ProductRepository products,
                       ApplicationIssueRepository issues,
                       ManualDecisionRepository manualDecisions,
                       DecisionSnapshotRepository snapshots,
                       ApplicationStateService state,
                       ApplicationQueryService query,
                       OfferService offerService) {
        this.apps            = apps;
        this.products        = products;
        this.issues          = issues;
        this.manualDecisions = manualDecisions;
        this.snapshots       = snapshots;
        this.state           = state;
        this.query           = query;
        this.offerService    = offerService;
    }

    @Transactional(readOnly = true)
    public PagedModel<ApplicationSummaryDto> list(ApplicationStatus status, Pageable pageable) {
        Page<Application> page = status == null
                ? apps.findAll(pageable)
                : apps.findByStatus(status, pageable);
        return new PagedModel<>(page.map(query::summary));
    }

    @Transactional(readOnly = true)
    public BankApplicationDetailDto get(UUID id) {
        return query.bankDetail(load(id));
    }

    @Transactional
    public BankApplicationDetailDto decide(AuthUser employee, UUID id, ManualDecisionRequest r) {
        Application a = load(id);

        if (a.getStatus() != ApplicationStatus.MANUAL_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Заявка не на ручном рассмотрении (статус: " + a.getStatus().clientLabel() + ")");
        }
        if (!Objects.equals(a.getVersion(), r.version())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Заявка была изменена. Обновите карточку и повторите");
        }

        boolean commentRequired = r.action() == ManualAction.REJECT
                || r.action() == ManualAction.REQUEST_INFO;
        if (commentRequired && (r.comment() == null || r.comment().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Для этого действия нужен комментарий — его увидит клиент");
        }

        ManualDecision md = new ManualDecision();
        md.setApplicationId(id);
        md.setEmployeeId(employee.id());
        md.setAction(r.action());
        md.setComment(r.comment());
        manualDecisions.save(md);

        Product p = products.findById(a.getProductId()).orElseThrow();

        switch (r.action()) {
            case APPROVE -> {
                offerService.create(a.getId(), a.getAmount(), a.getTermMonths(),
                        rateOrDefault(r.rate(), a, p), null);
                state.transition(a, ApplicationStatus.APPROVED, ActorType.EMPLOYEE, employee.id(),
                        "Сотрудник одобрил заявку", r.comment());
            }
            case CHANGE_TERMS -> {
                BigDecimal amount = r.amount() != null ? r.amount() : a.getAmount();
                int term = r.termMonths() != null ? r.termMonths() : a.getTermMonths();
                if (amount.compareTo(p.getMinAmount()) < 0 || amount.compareTo(p.getMaxAmount()) > 0
                        || term < p.getMinTermMonths() || term > p.getMaxTermMonths()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "Новые условия выходят за границы продукта «" + p.getName() + "»");
                }
                Map<String, Object> changes = new LinkedHashMap<>();
                if (amount.compareTo(a.getAmount()) != 0)
                    changes.put("amount", Map.of("from", a.getAmount(), "to", amount));
                if (term != a.getTermMonths())
                    changes.put("term_months", Map.of("from", a.getTermMonths(), "to", term));
                changes.put("reasons", r.comment() == null || r.comment().isBlank()
                        ? List.of() : List.of(r.comment()));
                offerService.create(a.getId(), amount, term, rateOrDefault(r.rate(), a, p), changes);
                state.transition(a, ApplicationStatus.APPROVED, ActorType.EMPLOYEE, employee.id(),
                        "Сотрудник изменил условия", r.comment());
            }
            case REJECT ->
                    state.transition(a, ApplicationStatus.REJECTED, ActorType.EMPLOYEE, employee.id(),
                            r.comment(), null);
            case REQUEST_INFO -> {
                ApplicationIssue issue = new ApplicationIssue();
                issue.setApplicationId(id);
                issue.setSource(IssueSource.EMPLOYEE);
                issue.setField("general");
                issue.setMessage(r.comment());
                issue.setReason("Запрос сотрудника банка");
                issues.save(issue);
                state.transition(a, ApplicationStatus.NEEDS_INFO, ActorType.EMPLOYEE, employee.id(),
                        "Сотрудник запросил дополнительные сведения", r.comment());
            }
        }
        return query.bankDetail(a);
    }

    // ─── внутренние ───────────────────────────────────────────────────────────

    private BigDecimal rateOrDefault(BigDecimal requested, Application a, Product p) {
        if (requested != null) return requested.setScale(2, RoundingMode.HALF_UP);
        Object fromDecision = snapshots.findFirstByApplicationIdOrderByCreatedAtDesc(a.getId())
                .map(s -> s.getPayload().get("final_terms"))
                .filter(Map.class::isInstance)
                .map(m -> ((Map<?, ?>) m).get("rate")).orElse(null);
        if (fromDecision instanceof Number n)
            return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP);
        return p.getBaseRate();
    }

    private Application load(UUID id) {
        return apps.findById(id).orElseThrow(() -> new NotFoundException("Заявка не найдена"));
    }
}
