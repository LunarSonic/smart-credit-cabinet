package ru.backend.offer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ContractDto(UUID id, UUID offerId, UUID applicationId, BigDecimal amount, int termMonths,
                              BigDecimal rate, Instant signedAt, Instant disbursedAt, boolean simulated, String status) {}
