package ru.backend.application.dto;

import ru.backend.application.entity.enums.CheckStatus;
import ru.backend.application.entity.enums.CheckType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CheckDto(UUID id, CheckType checkType, CheckStatus status, Map<String, Object> payload, Instant checkedAt) {
}
