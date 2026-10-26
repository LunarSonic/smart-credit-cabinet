package ru.backend.decision.client;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Тело POST /decide. JSON в snake_case — контракт с Python-сервисом (docs/ml-contract.md) */

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MlDecideRequest(UUID applicationId,
                              String requestId,
                              Map<String, Object> product,
                              Map<String, Object> application,
                              Map<String, Object> company,
                              Map<String, Object> account,
                              Map<String, Object> bki,
                              Map<String, Object> declaredObligations,
                              List<Map<String, Object>> checks) {}
