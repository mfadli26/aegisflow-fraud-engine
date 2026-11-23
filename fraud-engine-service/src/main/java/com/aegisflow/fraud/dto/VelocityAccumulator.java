package com.aegisflow.fraud.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VelocityAccumulator(
        long txCount,
        BigDecimal totalAmount,
        Instant windowStart
) {
}
