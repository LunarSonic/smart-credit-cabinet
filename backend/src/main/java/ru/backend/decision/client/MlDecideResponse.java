package ru.backend.decision.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlDecideResponse(
        String decisionId,
        String scoringResultId,
        BigDecimal score,
        BigDecimal probabilityOfDefault,
        String riskClass,
        List<Map<String, Object>> topFactors,
        String modelType,
        String modelVersion,
        String decision,
        Map<String, Object> finalTerms,
        Map<String, Object> changes,
        List<String> reasons,
        List<String> clientReasons,
        List<String> missingData,
        String policyVersion) {}
