package com.aegisflow.investigation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountRiskRankDto(
        int riskRank,
        String accountId,
        String holderName,
        String riskTier,
        boolean blacklisted,
        long totalEvaluated,
        long blockedCount,
        long reviewCount,
        BigDecimal avgRiskScore,
        int maxRiskScore,
        BigDecimal p95LatencyMs,
        Instant lastEvaluatedAt
) {
}