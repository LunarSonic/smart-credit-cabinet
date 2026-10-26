package ru.backend.offer.event;

import java.util.UUID;

public record ContractSignedEvent(UUID contractId) {}
