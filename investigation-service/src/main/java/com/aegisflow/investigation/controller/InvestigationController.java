package com.aegisflow.investigation.controller;

import com.aegisflow.common.dto.ApiResponse;
import com.aegisflow.investigation.dto.AccountRiskRankDto;
import com.aegisflow.investigation.dto.AnomalyZScoreDto;
import com.aegisflow.investigation.dto.AuditSearchResponseDto;
import com.aegisflow.investigation.dto.CircularTransferDto;
import com.aegisflow.investigation.dto.FraudAnalyticsSummaryDto;
import com.aegisflow.investigation.repository.FraudAnalyticsNativeRepository;
import com.aegisflow.investigation.service.AuditSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/investigations")
public class InvestigationController {

    private final FraudAnalyticsNativeRepository nativeRepository;
    private final AuditSearchService auditSearchService;

    public InvestigationController(
            FraudAnalyticsNativeRepository nativeRepository,
            AuditSearchService auditSearchService) {
        this.nativeRepository = nativeRepository;
        this.auditSearchService = auditSearchService;
    }

    @GetMapping("/sql/circular-transfers")
    public ResponseEntity<ApiResponse<List<CircularTransferDto>>> detectCircularTransfers(
            @RequestParam(defaultValue = "24") int lookbackHours,
            @RequestParam(defaultValue = "4") int maxHops) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Circular transfer ring detection executed via PostgreSQL Recursive CTE",
                nativeRepository.findCircularTransfers(lookbackHours, maxHops)));
    }

    @GetMapping("/sql/amount-anomalies")
    public ResponseEntity<ApiResponse<List<AnomalyZScoreDto>>> detectAmountAnomalies(
            @RequestParam(defaultValue = "30") int lookbackDays,
            @RequestParam(defaultValue = "1.0") double minZScore,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Statistical Z-score anomaly analysis executed via PostgreSQL Window Functions",
                nativeRepository.findStatisticalAmountAnomalies(lookbackDays, minZScore, limit)));
    }

    @GetMapping("/sql/risk-leaderboard")
    public ResponseEntity<ApiResponse<List<AccountRiskRankDto>>> getRiskLeaderboard() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Account risk leaderboard executed via FILTER, PERCENTILE_CONT(0.95), and DENSE_RANK",
                nativeRepository.findAccountRiskLeaderboard()));
    }

    @GetMapping("/sql/explain-plan")
    public ResponseEntity<ApiResponse<List<String>>> getExplainPlan() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Live PostgreSQL EXPLAIN ANALYZE plan for high-risk partial index query",
                nativeRepository.explainHighRiskDecisionQuery()));
    }

    @GetMapping("/elastic/search")
    public ResponseEntity<ApiResponse<AuditSearchResponseDto>> searchAuditLogs(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String decisionStatus,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Elasticsearch multi-criteria & fuzzy search completed",
                auditSearchService.searchAuditLogs(accountId, riskLevel, decisionStatus, minScore, keyword, page, size)));
    }

    @GetMapping("/elastic/analytics")
    public ResponseEntity<ApiResponse<FraudAnalyticsSummaryDto>> getElasticAnalytics() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Elasticsearch real-time fraud aggregations completed",
                auditSearchService.getRealtimeAggregations()));
    }
}