package ru.backend.application.dto;

import ru.backend.application.entity.enums.ActorType;
import ru.backend.application.entity.enums.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Одна запись истории смены статуса */

public record HistoryDto(
        Long id,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        String toStatusLabel,
        ActorType actorType,
        UUID actorId,
        String reason,
        String comment,
        Instant createdAt
) {}
