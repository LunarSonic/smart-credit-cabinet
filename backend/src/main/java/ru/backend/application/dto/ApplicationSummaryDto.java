package ru.backend.application.dto;

import ru.backend.application.entity.enums.ApplicationStatus;
import ru.backend.product.entity.enums.Purpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Краткая карточка заявки для списков.
 *
 * status / statusLabel — внутренний статус (виден сотруднику / в истории)
 * clientStatus        — сгруппированный клиентский статус (см. ApplicationStatus.clientStatus())
 * actionRequired      — true, если от клиента требуется действие (принять оферту / дополнить данные)
 */

public record ApplicationSummaryDto(
        UUID id,
        UUID companyId,
        String companyName,
        String productCode,
        String productName,
        BigDecimal amount,
        Integer termMonths,
        Purpose purpose,
        String purposeLabel,
        ApplicationStatus status,
        String statusLabel,
        String clientStatus,
        boolean actionRequired,
        Long version,
        Instant createdAt,
        Instant submittedAt,
        Instant updatedAt
) {}
