package com.aegisflow.fraud.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateRuleRequest(
        @Min(0) @Max(100) Integer weight,
        @DecimalMin("0.01") Double thresholdValue,
        Boolean enabled
) {
}
