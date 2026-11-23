package com.aegisflow.investigation.consumer;

import com.aegisflow.common.constant.KafkaTopics;
import com.aegisflow.common.event.RiskScoredEvent;
import com.aegisflow.common.event.RuleViolationDetail;
import com.aegisflow.investigation.document.FraudAuditDocument;
import com.aegisflow.investigation.repository.FraudAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ScoredEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScoredEventConsumer.class);
    private final FraudAuditRepository fraudAuditRepository;

    public ScoredEventConsumer(FraudAuditRepository fraudAuditRepository) {
        this.fraudAuditRepository = fraudAuditRepository;
    }

    @KafkaListener(
            topics = KafkaTopics.TX_SCORED,
            containerFactory = "riskScoredKafkaListenerContainerFactory"
    )
    public void onRiskScored(RiskScoredEvent event) {
        List<String> ruleCodes = event.violations() == null ? List.of() :
                event.violations().stream()
                        .map(RuleViolationDetail::ruleCode)
                        .toList();

        String violationSummary = event.violations() == null ? "" :
                event.violations().stream()
                        .map(v -> v.ruleCode() + ": " + v.reason())
                        .collect(Collectors.joining(" | "));

        FraudAuditDocument doc = new FraudAuditDocument();
        doc.setDecisionId(event.decisionId().toString());
        doc.setTxId(event.txId().toString());
        doc.setSourceAccountId(event.sourceAccountId());
        doc.setTargetAccountId(event.targetAccountId());
        doc.setAmount(event.amount().doubleValue());
        doc.setCurrency(event.currency());
        doc.setMerchantName(event.merchantName());
        doc.setCountryCode(event.countryCode());
        doc.setCity(event.city());
        doc.setRiskScore(event.riskScore());
        doc.setRiskLevel(event.riskLevel().name());
        doc.setDecisionStatus(event.decisionStatus().name());
        doc.setWindowTxCount1m(event.windowTxCount1m());
        doc.setTriggeredRules(ruleCodes);
        doc.setViolationSummary(violationSummary);
        doc.setEvaluationLatencyMs(event.evaluationLatencyMs());
        doc.setEvaluatedAt(event.evaluatedAt());

        fraudAuditRepository.save(doc);
        log.info("Indexed FraudAuditDocument decisionId={} txId={} riskLevel={} into Elasticsearch",
                doc.getDecisionId(), doc.getTxId(), doc.getRiskLevel());
    }
}