package com.aegisflow.investigation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnomalyZScoreDto(
        int anomalyRank,
        UUID txId,
        String sourceAccountId,
        String holderName,
        BigDecimal amount,
        BigDecimal accountAvgAmount,
        BigDecimal accountStddevAmount,
        BigDecimal zScore,
        Long secondsSincePrevTx,
        BigDecimal cumulativeAccountSpend,
        String merchantName,
        String city,
        String countryCode,
        String status,
        Instant createdAt
) {
}