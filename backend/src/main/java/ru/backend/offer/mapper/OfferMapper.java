package ru.backend.offer.mapper;

import ru.backend.application.entity.Application;
import ru.backend.offer.dto.ContractDto;
import ru.backend.offer.dto.OfferDto;
import ru.backend.offer.entity.Contract;
import ru.backend.offer.entity.Offer;

import org.springframework.stereotype.Component;

@Component
public class OfferMapper {
    public OfferDto toDto(Offer o) {
        return new OfferDto(o.getId(), o.getAmount(), o.getTermMonths(), o.getRate(), o.isTermsChanged(),
                o.getChanges(), o.getStatus(), o.getValidUntil(), o.getCreatedAt());
    }

    public ContractDto toDto(Contract c, Offer o, Application a) {
        String status = c.getDisbursedAt() != null ? "Выдано" : "Подписано";
        return new ContractDto(c.getId(), o.getId(), a.getId(), o.getAmount(), o.getTermMonths(), o.getRate(),
                c.getSignedAt(), c.getDisbursedAt(), c.isSimulated(), status);
    }
}
