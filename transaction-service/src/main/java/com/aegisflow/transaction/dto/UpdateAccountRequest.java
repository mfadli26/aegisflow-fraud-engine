package com.aegisflow.transaction.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record UpdateAccountRequest(
        @DecimalMin("1.00") BigDecimal dailyLimit,
        Boolean blacklisted,
        String riskTier
) {
}
