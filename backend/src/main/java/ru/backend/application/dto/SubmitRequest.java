package ru.backend.application.dto;

public record SubmitRequest(
        boolean bkiConsent,
        boolean personalDataConsent,
        Long version
) {}
