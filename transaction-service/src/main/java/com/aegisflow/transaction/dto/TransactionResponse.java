package com.aegisflow.transaction.dto;

import com.aegisflow.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID txId,
        String idempotencyKey,
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String currency,
        TransactionType txType,
        String merchantName,
        String countryCode,
        String city,
        String status,
        boolean replayed,
        Instant createdAt
) {
}
