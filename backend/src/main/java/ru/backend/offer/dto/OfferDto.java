package ru.backend.offer.dto;

import ru.backend.offer.entity.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OfferDto(UUID id, BigDecimal amount, int termMonths, BigDecimal rate, boolean termsChanged,
                           Map<String, Object> changes, OfferStatus status, Instant validUntil, Instant createdAt) {}
