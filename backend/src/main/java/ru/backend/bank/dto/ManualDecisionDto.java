package ru.backend.bank.dto;

import ru.backend.bank.entity.enums.ManualAction;

import java.time.Instant;
import java.util.UUID;

public record ManualDecisionDto(UUID id, UUID employeeId, ManualAction action, String comment, Instant createdAt) {}
