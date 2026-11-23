package com.aegisflow.fraud.dto;

import com.aegisflow.common.event.RiskScoredEvent;

public record ScoringOutcome(
        boolean success,
        RiskScoredEvent scoredEvent,
        String errorPayload
) {
    public static ScoringOutcome ok(RiskScoredEvent event) {
        return new ScoringOutcome(true, event, null);
    }

    public static ScoringOutcome failure(String errorPayload) {
        return new ScoringOutcome(false, null, errorPayload);
    }
}
