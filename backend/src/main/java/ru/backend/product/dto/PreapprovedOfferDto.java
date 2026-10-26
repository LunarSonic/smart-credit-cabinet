package ru.backend.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PreapprovedOfferDto(UUID id, UUID productId, String productCode, String productName,
                                      BigDecimal maxAmount, BigDecimal rateFrom, Instant validUntil,
                                      Map<String, Object> reason) {}
