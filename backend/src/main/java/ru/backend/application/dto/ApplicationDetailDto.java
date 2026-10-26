package ru.backend.application.dto;

import ru.backend.application.entity.enums.ConsentType;
import ru.backend.offer.dto.OfferDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Полная карточка заявки для клиента.
 * Включает сводку, задекларированные данные, замечания, историю, оферту, согласия
 * и понятные клиенту причины решения из ML-сервиса
 */

public record ApplicationDetailDto(
        ApplicationSummaryDto summary,

        String fullName,
        String phone,
        BigDecimal revenue6m,
        BigDecimal monthlyExpenses,

        List<ObligationDto> obligations,
        List<IssueDto> issues,
        List<HistoryDto> history,
        OfferDto offer,
        List<ConsentType> consents,

        List<String> clientReasons
) {}
