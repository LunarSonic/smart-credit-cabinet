package ru.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Задекларированное долговое обязательство клиента.
 * remainingDebt — необязателен (может не знать точного остатка)
 */

public record ObligationDto(
        @NotBlank
        String creditorName,

        @NotNull
        @Positive
        BigDecimal monthlyPayment,

        @PositiveOrZero
        BigDecimal remainingDebt
) {}
