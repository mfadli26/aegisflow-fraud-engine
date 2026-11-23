package com.aegisflow.transaction.dto;

import java.math.BigDecimal;

public record AccountProfileDto(
        String accountId,
        String holderName,
        String homeCountry,
        String homeCity,
        BigDecimal dailyLimit,
        BigDecimal avgTxAmount,
        boolean blacklisted,
        String riskTier
) {
}
