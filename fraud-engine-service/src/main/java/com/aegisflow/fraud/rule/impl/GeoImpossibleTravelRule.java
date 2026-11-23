package com.aegisflow.fraud.rule.impl;

import com.aegisflow.common.event.RuleViolationDetail;
import com.aegisflow.fraud.dto.AccountGeoSnapshot;
import com.aegisflow.fraud.rule.FraudRuleEvaluator;
import com.aegisflow.fraud.rule.RuleEvaluationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Order(4)
@ConditionalOnProperty(prefix = "aegis.fraud.rules.geo-impossible-travel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GeoImpossibleTravelRule implements FraudRuleEvaluator {

    public static final String CODE = "GEO_IMPOSSIBLE_TRAVEL";
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MIN_SIGNIFICANT_DISTANCE_KM = 50.0;

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public String getCategory() {
        return "LOCATION";
    }

    @Override
    public Optional<RuleViolationDetail> evaluate(RuleEvaluationContext context) {
        return context.activeConfig(CODE).flatMap(cfg -> context.lastGeoSnapshot().flatMap(prev -> {
            double distanceKm = haversineKm(
                    prev.latitude(), prev.longitude(),
                    context.event().latitude(), context.event().longitude());

            if (distanceKm < MIN_SIGNIFICANT_DISTANCE_KM) {
                return Optional.empty();
            }

            long elapsedSeconds = Math.abs(Duration.between(prev.recordedAt(), context.event().createdAt()).getSeconds());
            double elapsedHours = Math.max(elapsedSeconds, 1L) / 3600.0;
            double requiredSpeedKmh = distanceKm / elapsedHours;

            if (requiredSpeedKmh <= cfg.thresholdValue()) {
                return Optional.empty();
            }

            String reason = String.format(
                    "Impossible travel from %s (%s) to %s (%s): %.1f km in %ds (%.0f km/h > %.0f km/h)",
                    prev.city(), prev.countryCode(),
                    context.event().city(), context.event().countryCode(),
                    distanceKm, elapsedSeconds, requiredSpeedKmh, cfg.thresholdValue());

            return Optional.of(new RuleViolationDetail(CODE, getCategory(), cfg.weight(), reason));
        }));
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
