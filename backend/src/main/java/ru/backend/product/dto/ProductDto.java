package ru.backend.product.dto;

import ru.backend.product.entity.enums.Purpose;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductDto(UUID id, String code, String name, String description, Purpose suitablePurpose,
                             BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths, int maxTermMonths,
                             BigDecimal baseRate) {}
