package com.aegisflow.investigation.dto;

import java.util.List;

public record FraudAnalyticsSummaryDto(
        long totalIndexedDecisions,
        double averageRiskScore,
        List<BucketCountDto> riskLevelBreakdown,
        List<BucketCountDto> decisionStatusBreakdown,
        List<BucketCountDto> topTriggeredRules,
        List<BucketCountDto> topRiskyAccounts,
        List<BucketCountDto> hourlyTrend
) {
}