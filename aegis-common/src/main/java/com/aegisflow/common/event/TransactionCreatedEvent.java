package com.aegisflow.common.event;

import com.aegisflow.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCreatedEvent(
        UUID txId,
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String currency,
        TransactionType txType,
        String merchantName,
        String merchantCategory,
        String countryCode,
        String city,
        double latitude,
        double longitude,
        String deviceId,
        String ipAddress,
        Instant createdAt
) {
}