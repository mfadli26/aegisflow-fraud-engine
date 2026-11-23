package com.aegisflow.common.event;

public record RuleViolationDetail(
        String ruleCode,
        String category,
        int scoreContribution,
        String reason
) {
}