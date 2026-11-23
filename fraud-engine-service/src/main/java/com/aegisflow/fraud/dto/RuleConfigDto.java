package com.aegisflow.fraud.dto;

import java.time.Instant;

public record RuleConfigDto(
        String ruleCode,
        String ruleName,
        String category,
        String description,
        int weight,
        double thresholdValue,
        boolean enabled,
        Instant updatedAt
) {
}
