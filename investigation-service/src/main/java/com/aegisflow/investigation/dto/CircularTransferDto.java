package com.aegisflow.investigation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CircularTransferDto(
        String startAccount,
        String cyclePath,
        int hopCount,
        BigDecimal totalAmount,
        BigDecimal minHopAmount,
        long durationSeconds,
        Instant firstTxAt,
        Instant lastTxAt
) {
}