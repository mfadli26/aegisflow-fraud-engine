package com.aegisflow.fraud.rule.impl;

import com.aegisflow.common.event.RuleViolationDetail;
import com.aegisflow.fraud.rule.FraudRuleEvaluator;
import com.aegisflow.fraud.rule.RuleEvaluationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
@Order(3)
@ConditionalOnProperty(prefix = "aegis.fraud.rules.amount-anomaly", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AmountAnomalyRule implements FraudRuleEvaluator {

    public static final String CODE = "AMOUNT_ANOMALY";

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public String getCategory() {
        return "MONETARY";
    }

    @Override
    public Optional<RuleViolationDetail> evaluate(RuleEvaluationContext context) {
        return context.activeConfig(CODE).flatMap(cfg -> {
            BigDecimal avg = context.sourceAccount().getAvgTxAmount();
            if (avg == null || avg.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }

            BigDecimal txAmount = context.event().amount();
            double ratio = txAmount.divide(avg, 2, RoundingMode.HALF_UP).doubleValue();

            if (ratio < cfg.thresholdValue()) {
                return Optional.empty();
            }

            int dynamicScore = ratio >= cfg.thresholdValue() * 2.0
                    ? Math.min(100, cfg.weight() + 15)
                    : cfg.weight();

            String reason = String.format(
                    "Transaction amount %.2f is %.2fx above historical average %.2f (threshold=%.1fx)",
                    txAmount, ratio, avg, cfg.thresholdValue());

            return Optional.of(new RuleViolationDetail(CODE, getCategory(), dynamicScore, reason));
        });
    }
}
