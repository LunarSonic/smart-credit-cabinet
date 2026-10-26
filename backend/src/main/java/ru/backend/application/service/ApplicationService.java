package ru.backend.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.backend.application.dto.ApplicationDetailDto;
import ru.backend.application.dto.ApplicationSummaryDto;
import ru.backend.application.dto.ObligationDto;
import ru.backend.application.dto.UpdateApplicationRequest;
import ru.backend.application.dto.SubmitRequest;
import ru.backend.application.dto.CreateApplicationRequest;
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
import ru.backend.application.event.ApplicationSubmittedEvent;
import ru.backend.application.repository.ApplicationIssueRepository;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.repository.CheckResultRepository;
import ru.backend.application.repository.ConsentRepository;
import ru.backend.application.repository.DeclaredObligationRepository;
import ru.backend.common.BusinessException;
import ru.backend.common.NotFoundException;
import ru.backend.product.entity.PreapprovedOffer;
import ru.backend.product.entity.Product;
import ru.backend.product.repository.PreapprovedOfferRepository;
import ru.backend.product.repository.ProductRepository;
import ru.backend.session.AuthUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApplicationService {

    public static final String CONSENT_TEXT_VERSION = "2026-10-v1";

    private final ApplicationRepository apps;
    private final ProductRepository products;
    private final PreapprovedOfferRepository preapproved;
    private final DeclaredObligationRepository obligations;
    private final ConsentRepository consents;
    private final ApplicationIssueRepository issues;
    private final CheckResultRepository checks;
    private final ApplicationStateService state;
    private final ApplicationQueryService query;
    private final ApplicationEventPublisher events;

    public ApplicationService(ApplicationRepository apps, ProductRepository products,
                              PreapprovedOfferRepository preapproved,
                              DeclaredObligationRepository obligations,
                              ConsentRepository consents,
                              ApplicationIssueRepository issues,
                              CheckResultRepository checks,
                              ApplicationStateService state,
                              ApplicationQueryService query,
                              ApplicationEventPublisher events) {
        this.apps = apps;
        this.products = products;
        this.preapproved = preapproved;
        this.obligations = obligations;
        this.consents = consents;
        this.issues = issues;
        this.checks = checks;
        this.state = state;
        this.query = query;
        this.events = events;
    }

    @Transactional
    public ApplicationDetailDto create(AuthUser u, CreateApplicationRequest r) {
        UUID companyId = requireCompany(u);
        Product p = products.findById(r.productId()).filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Продукт не найден"));

        Application a = new Application();
        a.setCompanyId(companyId);
        a.setProductId(p.getId());
        a.setCreatedBy(u.id());
        a.setStatus(ApplicationStatus.DRAFT);
        a.setAmount(r.amount());
        a.setTermMonths(r.termMonths());
        a.setPurpose(r.purpose() != null ? r.purpose() : p.getSuitablePurpose());

        if (r.preapprovedOfferId() != null) {
            PreapprovedOffer po = preapproved.findById(r.preapprovedOfferId())
                    .filter(x -> x.getCompanyId().equals(companyId) && x.getProductId().equals(p.getId()))
                    .orElseThrow(() -> new NotFoundException("Предодобренное предложение не найдено"));
            a.setPreapprovedOfferId(po.getId());
            if (a.getAmount() == null) a.setAmount(po.getMaxAmount());
            if (a.getTermMonths() == null) a.setTermMonths(p.getMaxTermMonths());
        }

        a = apps.save(a);
        state.recordCreation(a, u.id());
        return query.detail(a);
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummaryDto> list(AuthUser u) {
        return apps.findByCompanyIdOrderByCreatedAtDesc(requireCompany(u))
                .stream().map(query::summary).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDetailDto get(AuthUser u, UUID id) {
        return query.detail(loadOwned(u, id));
    }

    @Transactional
    public ApplicationDetailDto update(AuthUser u, UUID id, UpdateApplicationRequest r) {
        Application a = loadOwned(u, id);
        if (a.getStatus() != ApplicationStatus.DRAFT && a.getStatus() != ApplicationStatus.NEEDS_INFO) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Заявку нельзя изменить в статусе «" + a.getStatus().clientLabel() + "»");
        }
        checkVersion(a, r.version());

        if (r.amount()          != null) a.setAmount(r.amount());
        if (r.termMonths()      != null) a.setTermMonths(r.termMonths());
        if (r.purpose()         != null) a.setPurpose(r.purpose());
        if (r.fullName()        != null) a.setFullName(r.fullName());
        if (r.phone()           != null) a.setPhone(r.phone());
        if (r.revenue6m()       != null) a.setRevenue6m(r.revenue6m());
        if (r.monthlyExpenses() != null) a.setMonthlyExpenses(r.monthlyExpenses());

        if (r.obligations() != null) {
            obligations.deleteByApplicationId(a.getId());
            obligations.flush();
            for (ObligationDto o : r.obligations()) {
                DeclaredObligation d = new DeclaredObligation();
                d.setApplicationId(a.getId());
                d.setCreditorName(o.creditorName());
                d.setMonthlyPayment(o.monthlyPayment());
                d.setRemainingDebt(o.remainingDebt());
                obligations.save(d);
            }
        }
        a = apps.saveAndFlush(a);
        return query.detail(a);
    }

    @Transactional
    public ApplicationDetailDto submit(AuthUser u, UUID id, SubmitRequest r) {
        Application a = loadOwned(u, id);
        if (a.getStatus() != ApplicationStatus.DRAFT && a.getStatus() != ApplicationStatus.NEEDS_INFO) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Заявку нельзя отправить в статусе «" + a.getStatus().clientLabel() + "»");
        }
        if (r.version() != null) checkVersion(a, r.version());

        giveConsent(a, u, ConsentType.BKI_REQUEST, r.bkiConsent());
        giveConsent(a, u, ConsentType.PERSONAL_DATA, r.personalDataConsent());

        if (!consents.existsByApplicationIdAndType(a.getId(), ConsentType.BKI_REQUEST)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Без согласия на запрос в БКИ заявку отправить нельзя");
        }
        if (!consents.existsByApplicationIdAndType(a.getId(), ConsentType.PERSONAL_DATA)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Без согласия на обработку персональных данных заявку отправить нельзя");
        }

        Instant now = Instant.now();
        issues.findByApplicationIdAndResolvedFalse(a.getId())
                .forEach(i -> { i.setResolved(true); i.setResolvedAt(now); });

        boolean resubmit = a.getStatus() == ApplicationStatus.NEEDS_INFO;

        if (resubmit) {
            // Клиент дополнил данные — пропускаем повторную проверку полноты,
            // идём сразу на Data Check (правило спецификации: NEEDS_INFO → DATA_CHECK).
            // Фиксируем новый результат проверки полноты как пройденную.
            CheckResult cr = new CheckResult();
            cr.setApplicationId(a.getId());
            cr.setCheckType(CheckType.COMPLETENESS);
            cr.setStatus(CheckStatus.OK);
            cr.setPayload(Map.of("issues_count", 0, "resubmit", true));
            checks.save(cr);
            state.transition(a, ApplicationStatus.DATA_CHECK, ActorType.CLIENT, u.id(),
                    "Клиент дополнил данные", null);
        } else {
            if (a.getSubmittedAt() == null) a.setSubmittedAt(now);
            state.transition(a, ApplicationStatus.SUBMITTED, ActorType.CLIENT, u.id(),
                    "Клиент отправил заявку", null);
        }

        events.publishEvent(new ApplicationSubmittedEvent(a.getId()));
        return query.detail(a);
    }

    /** Отзыв заявки клиентом (DRAFT, SUBMITTED, NEEDS_INFO, MANUAL_REVIEW, APPROVED). */
    @Transactional
    public ApplicationDetailDto cancel(AuthUser u, UUID id) {
        Application a = loadOwned(u, id);
        String reason = a.getStatus() == ApplicationStatus.DRAFT
                ? "Клиент удалил черновик"
                : "Клиент отозвал заявку";
        state.transition(a, ApplicationStatus.CANCELLED, ActorType.CLIENT, u.id(), reason, null);
        return query.detail(a);
    }

    // ─── утилиты ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Application loadOwned(AuthUser u, UUID id) {
        UUID companyId = requireCompany(u);
        return apps.findById(id)
                .filter(a -> a.getCompanyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
    }

    private void giveConsent(Application a, AuthUser u, ConsentType type, boolean given) {
        if (!given || consents.existsByApplicationIdAndType(a.getId(), type)) return;
        Consent c = new Consent();
        c.setApplicationId(a.getId());
        c.setType(type);
        c.setTextVersion(CONSENT_TEXT_VERSION);
        c.setGivenBy(u.id());
        consents.save(c);
    }

    private void checkVersion(Application a, Long expected) {
        if (expected != null && !Objects.equals(a.getVersion(), expected)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Заявка была изменена, обновите страницу и повторите");
        }
    }

    private UUID requireCompany(AuthUser u) {
        if (u.companyId() == null)
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "У пользователя нет привязки к компании");
        return u.companyId();
    }
}
