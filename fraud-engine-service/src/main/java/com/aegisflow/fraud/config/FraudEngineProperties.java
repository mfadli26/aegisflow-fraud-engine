package com.aegisflow.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aegis.fraud")
public record FraudEngineProperties(
        int blockThreshold,
        int reviewThreshold,
        long slidingWindowSeconds
) {
}
