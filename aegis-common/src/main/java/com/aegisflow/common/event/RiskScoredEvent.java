package com.aegisflow.common.event;

import com.aegisflow.common.enums.DecisionStatus;
import com.aegisflow.common.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskScoredEvent(
        UUID decisionId,
        UUID txId,
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String currency,
        String merchantName,
        String countryCode,
        String city,
        int riskScore,
        RiskLevel riskLevel,
        DecisionStatus decisionStatus,
        long windowTxCount1m,
        BigDecimal windowTxAmount1m,
        List<RuleViolationDetail> violations,
        long evaluationLatencyMs,
        Instant evaluatedAt
) {
}