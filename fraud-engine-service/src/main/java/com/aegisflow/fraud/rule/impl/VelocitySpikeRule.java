package com.aegisflow.fraud.rule.impl;

import com.aegisflow.common.event.RuleViolationDetail;
import com.aegisflow.fraud.rule.FraudRuleEvaluator;
import com.aegisflow.fraud.rule.RuleEvaluationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(2)
@ConditionalOnProperty(prefix = "aegis.fraud.rules.velocity-spike", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VelocitySpikeRule implements FraudRuleEvaluator {

    public static final String CODE = "VELOCITY_SPIKE";

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public String getCategory() {
        return "BEHAVIORAL";
    }

    @Override
    public Optional<RuleViolationDetail> evaluate(RuleEvaluationContext context) {
        return context.activeConfig(CODE).flatMap(cfg -> {
            long effectiveCount = Math.max(context.kafkaWindowTxCount1m(), context.redisSlidingWindowCount1m());
            long threshold = (long) cfg.thresholdValue();

            if (effectiveCount < threshold) {
                return Optional.empty();
            }

            // Escalate weight slightly if burst is significantly above threshold
            int dynamicScore = effectiveCount >= threshold * 2
                    ? Math.min(100, cfg.weight() + 20)
                    : cfg.weight();

            String reason = String.format(
                    "High transaction velocity detected: %d tx in 1m window (kafka=%d, redis=%d, threshold=%d)",
                    effectiveCount, context.kafkaWindowTxCount1m(), context.redisSlidingWindowCount1m(), threshold);

            return Optional.of(new RuleViolationDetail(CODE, getCategory(), dynamicScore, reason));
        });
    }
}
