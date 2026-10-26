package ru.backend.application.dto;

import ru.backend.application.entity.enums.IssueSource;

import java.time.Instant;
import java.util.UUID;

/** Замечание к заявке: что нужно дополнить/уточнить */

public record IssueDto(
        UUID id,
        IssueSource source,
        String field,
        String message,
        String reason,
        boolean resolved,
        Instant createdAt
) {}
