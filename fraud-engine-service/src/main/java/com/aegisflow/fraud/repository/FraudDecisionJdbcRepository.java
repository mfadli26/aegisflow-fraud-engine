package com.aegisflow.fraud.repository;

import com.aegisflow.common.event.RiskScoredEvent;
import com.aegisflow.common.event.RuleViolationDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;

@Repository
public class FraudDecisionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public FraudDecisionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void persistDecisionAndUpdateTransaction(RiskScoredEvent event) {
        String[] triggeredRules = event.violations().stream()
                .map(RuleViolationDetail::ruleCode)
                .toArray(String[]::new);

        String upsertSql = """
                INSERT INTO fraud_decisions (
                    decision_id, tx_id, account_id, risk_score, risk_level,
                    decision_status, triggered_rules, evaluation_latency_ms, evaluated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tx_id) DO UPDATE SET
                    risk_score = EXCLUDED.risk_score,
                    risk_level = EXCLUDED.risk_level,
                    decision_status = EXCLUDED.decision_status,
                    triggered_rules = EXCLUDED.triggered_rules,
                    evaluation_latency_ms = EXCLUDED.evaluation_latency_ms,
                    evaluated_at = EXCLUDED.evaluated_at
                """;

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(upsertSql);
            Array rulesArray = connection.createArrayOf("text", triggeredRules);
            ps.setObject(1, event.decisionId());
            ps.setObject(2, event.txId());
            ps.setString(3, event.sourceAccountId());
            ps.setInt(4, event.riskScore());
            ps.setString(5, event.riskLevel().name());
            ps.setString(6, event.decisionStatus().name());
            ps.setArray(7, rulesArray);
            ps.setLong(8, event.evaluationLatencyMs());
            ps.setTimestamp(9, Timestamp.from(event.evaluatedAt()));
            return ps;
        });

        jdbcTemplate.update(
                "UPDATE transactions SET status = ? WHERE tx_id = ?",
                event.decisionStatus().name(),
                event.txId()
        );
    }
}
