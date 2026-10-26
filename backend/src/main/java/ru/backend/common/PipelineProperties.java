package ru.backend.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pipeline")
public record PipelineProperties(long recoveryAfterSeconds, long recoveryIntervalMs,
                                 long disbursementDelayMs, long offerTtlDays) {}
