package com.aegisflow.transaction.dto;

import com.aegisflow.common.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotBlank String sourceAccountId,
        String targetAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull TransactionType txType,
        String merchantName,
        String merchantCategory,
        @NotBlank @Size(min = 2, max = 3) String countryCode,
        @NotBlank String city,
        double latitude,
        double longitude,
        String deviceId,
        String ipAddress
) {
}
