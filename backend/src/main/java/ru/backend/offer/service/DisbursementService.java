package ru.backend.offer.service;

import ru.backend.application.entity.Application;
import ru.backend.application.entity.enums.ActorType;
import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.application.repository.ApplicationRepository;
import ru.backend.application.service.ApplicationStateService;
import ru.backend.offer.entity.Contract;
import ru.backend.offer.entity.Offer;
import ru.backend.offer.repository.ContractRepository;
import ru.backend.offer.repository.OfferRepository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Имитация выдачи денег: реальных операций нет, contract.simulated всегда true */

@Service
public class DisbursementService {
    private final ContractRepository contracts;
    private final OfferRepository offers;
    private final ApplicationRepository apps;
    private final ApplicationStateService state;

    public DisbursementService(ContractRepository contracts, OfferRepository offers, ApplicationRepository apps,
                               ApplicationStateService state) {
        this.contracts = contracts; this.offers = offers; this.apps = apps; this.state = state;
    }

    @Transactional
    public void disburse(UUID contractId) {
        Contract c = contracts.findById(contractId).orElse(null);
        if (c == null || c.getDisbursedAt() != null) return;   // идемпотентно
        Offer o = offers.findById(c.getOfferId()).orElseThrow();
        Application a = apps.findById(o.getApplicationId()).orElseThrow();
        if (a.getStatus() != ApplicationStatus.SIGNED) return;
        c.setDisbursedAt(Instant.now());
        state.transition(a, ApplicationStatus.DISBURSED, ActorType.SYSTEM, null,
                "Выдача смоделирована (реальных операций нет)", null);
    }
}
