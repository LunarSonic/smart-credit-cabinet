package ru.backend.product.dto;

import ru.backend.product.entity.enums.Purpose;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RecommendationRequest(@NotNull Purpose purpose, @NotNull @Positive BigDecimal amount,
                                        @NotNull @Positive Integer termMonths) {}
