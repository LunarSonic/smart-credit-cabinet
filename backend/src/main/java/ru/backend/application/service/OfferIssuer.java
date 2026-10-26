package ru.backend.application.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Интерфейс выпуска оферты — вызывается конвейером. Реализуется OfferService. */
public interface OfferIssuer {
    void issue(UUID applicationId, BigDecimal amount, int termMonths, BigDecimal rate,
               Map<String, Object> changes);
}
