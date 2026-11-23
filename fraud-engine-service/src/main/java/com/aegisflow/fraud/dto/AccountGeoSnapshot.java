package com.aegisflow.fraud.dto;

import java.time.Instant;

public record AccountGeoSnapshot(
        String accountId,
        String countryCode,
        String city,
        double latitude,
        double longitude,
        Instant recordedAt
) {
}
