package ru.backend.offer.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.backend.application.entity.Application;
import ru.backend.application.entity.enums.ActorType;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.service.ApplicationStateService;
import ru.backend.application.service.OfferIssuer;
import ru.backend.common.BusinessException;
import ru.backend.common.NotFoundException;
import ru.backend.common.PipelineProperties;
import ru.backend.offer.dto.ContractDto;
import ru.backend.offer.dto.OfferDto;
import ru.backend.offer.entity.Contract;
import ru.backend.offer.entity.Offer;
import ru.backend.offer.entity.enums.OfferStatus;
import ru.backend.offer.event.ContractSignedEvent;
import ru.backend.offer.mapper.OfferMapper;
import ru.backend.offer.repository.ContractRepository;
import ru.backend.offer.repository.OfferRepository;
import ru.backend.session.AuthUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Оферта: выпуск, принятие (имитация подписи), отказ, истечение, договоры клиента */

@Service
public class OfferService implements OfferIssuer {

    private final OfferRepository offers;
    private final ApplicationRepository apps;
    private final ContractRepository contracts;
    private final ApplicationStateService state;
    private final ApplicationEventPublisher events;
    private final PipelineProperties props;
    private final OfferMapper mapper;

    public OfferService(OfferRepository offers, ApplicationRepository apps, ContractRepository contracts,
                        ApplicationStateService state, ApplicationEventPublisher events,
                        PipelineProperties props, OfferMapper mapper) {
        this.offers    = offers;
        this.apps      = apps;
        this.contracts = contracts;
        this.state     = state;
        this.events    = events;
        this.props     = props;
        this.mapper    = mapper;
    }

    @Override
    @Transactional
    public void issue(UUID applicationId, BigDecimal amount, int termMonths, BigDecimal rate,
                      Map<String, Object> changes) {
        create(applicationId, amount, termMonths, rate, changes);
    }

    /** Создаёт активную оферту; предыдущая активная (если была) помечается истёкшей. */
    @Transactional
    public Offer create(UUID applicationId, BigDecimal amount, int termMonths, BigDecimal rate,
                        Map<String, Object> changes) {
        offers.findByApplicationIdAndStatus(applicationId, OfferStatus.ACTIVE)
                .forEach(o -> o.setStatus(OfferStatus.EXPIRED));
        offers.flush();

        Offer o = new Offer();
        o.setApplicationId(applicationId);
        o.setAmount(amount);
        o.setTermMonths(termMonths);
        o.setRate(rate);
        o.setTermsChanged(changes != null && !changes.isEmpty());
        o.setChanges(changes);
        o.setValidUntil(Instant.now().plus(props.offerTtlDays(), ChronoUnit.DAYS));
        return offers.save(o);
    }

    @Transactional(readOnly = true)
    public Optional<OfferDto> latest(UUID applicationId) {
        List<Offer> list = offers.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        return list.isEmpty() ? Optional.empty() : Optional.of(mapper.toDto(list.get(0)));
    }

    @Transactional
    public ContractDto accept(AuthUser u, UUID offerId) {
        Offer o = loadOwnedActive(u, offerId);
        Application a = apps.findById(o.getApplicationId()).orElseThrow();
        o.setStatus(OfferStatus.ACCEPTED);
        state.transition(a, ApplicationStatus.SIGNED, ActorType.CLIENT, u.id(),
                "Клиент принял оферту (имитация подписи)", null);
        Contract c = new Contract();
        c.setOfferId(o.getId());
        c.setSignedAt(Instant.now());
        c.setSignedBy(u.id());
        c.setSimulated(true);
        c = contracts.save(c);
        events.publishEvent(new ContractSignedEvent(c.getId()));
        return mapper.toDto(c, o, a);
    }

    /** Клиент отказывается от оферты → OFFER_DECLINED (конечный статус). */
    @Transactional
    public void decline(AuthUser u, UUID offerId) {
        Offer o = loadOwnedActive(u, offerId);
        Application a = apps.findById(o.getApplicationId()).orElseThrow();
        o.setStatus(OfferStatus.DECLINED);
        state.transition(a, ApplicationStatus.OFFER_DECLINED, ActorType.CLIENT, u.id(),
                "Клиент отказался от предложенных условий", null);
    }

    /** Вызывается фоновой задачей, когда истёк срок действия оферты. */
    @Transactional
    public void expire(UUID offerId) {
        Offer o = offers.findById(offerId).orElse(null);
        if (o == null || o.getStatus() != OfferStatus.ACTIVE) return;
        o.setStatus(OfferStatus.EXPIRED);
        Application a = apps.findById(o.getApplicationId()).orElseThrow();
        if (a.getStatus() == ApplicationStatus.APPROVED) {
            state.transition(a, ApplicationStatus.OFFER_EXPIRED, ActorType.SYSTEM, null,
                    "Истёк срок действия оферты", null);
        }
    }

    @Transactional(readOnly = true)
    public List<ContractDto> contractsOf(AuthUser u) {
        if (u.companyId() == null) return List.of();
        return contracts.findByCompany(u.companyId()).stream().map(c -> {
            Offer o = offers.findById(c.getOfferId()).orElseThrow();
            Application a = apps.findById(o.getApplicationId()).orElseThrow();
            return mapper.toDto(c, o, a);
        }).toList();
    }

    // ─── внутренние ───────────────────────────────────────────────────────────

    private Offer loadOwnedActive(AuthUser u, UUID offerId) {
        Offer o = offers.findById(offerId)
                .orElseThrow(() -> new NotFoundException("Оферта не найдена"));
        Application a = apps.findById(o.getApplicationId()).orElseThrow();
        if (u.companyId() == null || !u.companyId().equals(a.getCompanyId()))
            throw new NotFoundException("Оферта не найдена");
        if (o.getStatus() != OfferStatus.ACTIVE || a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "Оферта уже не активна");
        }
        if (o.getValidUntil().isBefore(Instant.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Срок действия оферты истёк");
        }
        return o;
    }
}
