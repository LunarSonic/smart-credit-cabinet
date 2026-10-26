package ru.backend.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.backend.product.entity.enums.Purpose;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Запрос на создание черновика заявки.
 * amount и termMonths не обязательны при создании — можно дозаполнить через PUT /applications/{id}.
 */
public record CreateApplicationRequest(
        @NotNull UUID productId,
        @Positive BigDecimal amount,
        @Positive Integer termMonths,
        Purpose purpose,
        UUID preapprovedOfferId
) {}
