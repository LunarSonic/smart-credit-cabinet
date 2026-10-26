package ru.backend.application.event;

import java.util.UUID;

public record ApplicationSubmittedEvent(UUID applicationId) {
}
