package ru.backend.decision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml")
public record MlProperties(String baseUrl, int connectTimeoutMs, int readTimeoutMs, int retries) {}
