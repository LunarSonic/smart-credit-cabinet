package ru.backend.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import ru.backend.product.entity.enums.Purpose;

import java.math.BigDecimal;
import java.util.List;

public record UpdateApplicationRequest(
        @Positive
        BigDecimal amount,

        @Positive
        Integer termMonths,
        Purpose purpose,
        String fullName,
        String phone,

        @PositiveOrZero
        BigDecimal revenue6m,

        @PositiveOrZero
        BigDecimal monthlyExpenses,

        @Valid
        List<ObligationDto> obligations,

        Long version
) {}
