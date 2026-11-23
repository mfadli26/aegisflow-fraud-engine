package com.aegisflow.fraud.dto;

import com.aegisflow.common.event.TransactionCreatedEvent;

import java.math.BigDecimal;

public record EnrichedTransaction(
        TransactionCreatedEvent event,
        long windowTxCount1m,
        BigDecimal windowTxAmount1m
) {
}
