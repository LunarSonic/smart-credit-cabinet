package ru.backend.bank.dto;

import ru.backend.bank.entity.enums.ManualAction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ManualDecisionRequest(@NotNull ManualAction action, String comment, @Positive BigDecimal amount,
                                        @Positive Integer termMonths, @Positive BigDecimal rate, @NotNull Long version) {}
