package com.aegisflow.fraud.service;

import com.aegisflow.common.enums.DecisionStatus;
import com.aegisflow.common.enums.RiskLevel;
import com.aegisflow.common.event.RiskScoredEvent;
import com.aegisflow.common.event.RuleViolationDetail;
import com.aegisflow.common.event.TransactionCreatedEvent;
import com.aegisflow.fraud.config.FraudEngineProperties;
import com.aegisflow.fraud.dto.AccountGeoSnapshot;
import com.aegisflow.fraud.dto.EnrichedTransaction;
import com.aegisflow.fraud.dto.RuleConfigDto;
import com.aegisflow.fraud.entity.AccountReadEntity;
import com.aegisflow.fraud.grid.DynamicRuleGridService;
import com.aegisflow.fraud.repository.AccountReadRepository;
import com.aegisflow.fraud.repository.FraudDecisionJdbcRepository;
import com.aegisflow.fraud.rule.RuleEvaluationContext;
import com.aegisflow.fraud.rule.RuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);

    private final RuleRegistry ruleRegistry;
    private final DynamicRuleGridService ruleGridService;
    private final AccountReadRepository accountReadRepository;
    private final FraudDecisionJdbcRepository decisionJdbcRepository;
    private final FraudEngineProperties properties;

    public RiskScoringService(
            RuleRegistry ruleRegistry,
            DynamicRuleGridService ruleGridService,
            AccountReadRepository accountReadRepository,
            FraudDecisionJdbcRepository decisionJdbcRepository,
            FraudEngineProperties properties) {
        this.ruleRegistry = ruleRegistry;
        this.ruleGridService = ruleGridService;
        this.accountReadRepository = accountReadRepository;
        this.decisionJdbcRepository = decisionJdbcRepository;
        this.properties = properties;
    }

    public RiskScoredEvent evaluateAndPersist(EnrichedTransaction enriched) {
        long startNanos = System.nanoTime();
        TransactionCreatedEvent event = enriched.event();

        AccountReadEntity sourceAccount = accountReadRepository.findById(event.sourceAccountId())
                .orElseThrow(() -> new IllegalStateException("Source account not found: " + event.sourceAccountId()));

        Optional<AccountReadEntity> targetAccount = Optional.ofNullable(event.targetAccountId())
                .filter(id -> !id.isBlank())
                .flatMap(accountReadRepository::findById);

        long redisSlidingCount = ruleGridService.recordAndCountSlidingWindow(
                event.sourceAccountId(),
                event.txId().toString(),
                event.createdAt() != null ? event.createdAt() : Instant.now()
        );

        Optional<AccountGeoSnapshot> lastGeo = ruleGridService.getLastGeoSnapshot(event.sourceAccountId());
        Map<String, RuleConfigDto> ruleConfigs = ruleGridService.getAllRulesSnapshot();

        RuleEvaluationContext context = new RuleEvaluationContext(
                event,
                sourceAccount,
                targetAccount,
                enriched.windowTxCount1m(),
                enriched.windowTxAmount1m(),
                redisSlidingCount,
                lastGeo,
                ruleConfigs
        );

        // Java Stream functional pipeline: execute all IoC rule beans, collect & sort violations
        List<RuleViolationDetail> violations = ruleRegistry.getEvaluators().stream()
                .map(evaluator -> evaluator.evaluate(context))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(RuleViolationDetail::scoreContribution).reversed())
                .toList();

        // Java Stream aggregation: group risk contributions by category & compute capped weighted total
        Map<String, Integer> scoreByCategory = violations.stream()
                .collect(Collectors.groupingBy(
                        RuleViolationDetail::category,
                        Collectors.summingInt(RuleViolationDetail::scoreContribution)
                ));

        int totalRiskScore = Math.min(100, scoreByCategory.values().stream().reduce(0, Integer::sum));
        RiskLevel riskLevel = RiskLevel.fromScore(totalRiskScore);
        DecisionStatus decisionStatus = resolveDecision(totalRiskScore);

        long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        RiskScoredEvent scoredEvent = new RiskScoredEvent(
                UUID.randomUUID(),
                event.txId(),
                event.sourceAccountId(),
                event.targetAccountId(),
                event.amount(),
                event.currency(),
                event.merchantName(),
                event.countryCode(),
                event.city(),
                totalRiskScore,
                riskLevel,
                decisionStatus,
                Math.max(enriched.windowTxCount1m(), redisSlidingCount),
                enriched.windowTxAmount1m(),
                violations,
                latencyMs,
                Instant.now()
        );

        decisionJdbcRepository.persistDecisionAndUpdateTransaction(scoredEvent);

        // Update last known geographic position in Redisson Data Grid
        ruleGridService.updateLastGeoSnapshot(new AccountGeoSnapshot(
                event.sourceAccountId(),
                event.countryCode(),
                event.city(),
                event.latitude(),
                event.longitude(),
                event.createdAt() != null ? event.createdAt() : Instant.now()
        ));

        log.info("Scored txId={} account={} score={} level={} decision={} categoryBreakdown={} latency={}ms",
                event.txId(), event.sourceAccountId(), totalRiskScore, riskLevel,
                decisionStatus, scoreByCategory, latencyMs);

        return scoredEvent;
    }

    private DecisionStatus resolveDecision(int score) {
        if (score >= properties.blockThreshold()) {
            return DecisionStatus.BLOCKED;
        }
        if (score >= properties.reviewThreshold()) {
            return DecisionStatus.REVIEW_REQUIRED;
        }
        return DecisionStatus.APPROVED;
    }
}
