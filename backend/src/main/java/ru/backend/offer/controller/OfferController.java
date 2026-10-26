package ru.backend.offer.controller;

import ru.backend.application.service.ApplicationService;
import ru.backend.common.NotFoundException;
import ru.backend.offer.dto.ContractDto;
import ru.backend.offer.dto.OfferDto;
import ru.backend.offer.service.OfferService;
import ru.backend.session.AuthUser;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Этап 8: оферта, подписание кнопкой (имитация), договоры («кредиты в кабинете») */

@RestController
@RequestMapping("/api")
public class OfferController {
    private final OfferService offers;
    private final ApplicationService applications;

    public OfferController(OfferService offers, ApplicationService applications) {
        this.offers = offers; this.applications = applications;
    }

    /** Оферта по заявке клиента (последняя). Появляется, когда заявка дошла до OFFER_READY */
    @GetMapping("/applications/{applicationId}/offer")
    public OfferDto offer(@AuthenticationPrincipal AuthUser u, @PathVariable UUID applicationId) {
        applications.loadOwned(u, applicationId);   // проверка владельца
        return offers.latest(applicationId).orElseThrow(() -> new NotFoundException("Оферта ещё не сформирована"));
    }

    @PostMapping("/offers/{id}/accept")
    public ContractDto accept(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) { return offers.accept(u, id); }

    @PostMapping("/offers/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) { offers.decline(u, id); }

    @GetMapping("/contracts")
    public List<ContractDto> contracts(@AuthenticationPrincipal AuthUser u) { return offers.contractsOf(u); }
}
