package ru.backend.application.service;

/** Замечание, обнаруженное при проверке полноты. Временный объект — не хранится в БД напрямую */

public record IssueDraft(String field, String message, String reason) {}
