package com.aegisflow.fraud.rule.impl;

import com.aegisflow.common.event.RuleViolationDetail;
import com.aegisflow.fraud.rule.FraudRuleEvaluator;
import com.aegisflow.fraud.rule.RuleEvaluationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "aegis.fraud.rules.blacklist-account", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BlacklistAccountRule implements FraudRuleEvaluator {

    public static final String CODE = "BLACKLIST_ACCOUNT";

    @Override
    public String getRuleCode() {
        return CODE;
    }

    @Override
    public String getCategory() {
        return "COMPLIANCE";
    }

    @Override
    public Optional<RuleViolationDetail> evaluate(RuleEvaluationContext context) {
        return context.activeConfig(CODE).flatMap(cfg -> {
            boolean sourceBlocked = context.sourceAccount().isBlacklisted();
            boolean targetBlocked = context.targetAccount()
                    .map(acc -> acc.isBlacklisted())
                    .orElse(false);

            if (!sourceBlocked && !targetBlocked) {
                return Optional.empty();
            }

            String reason = sourceBlocked
                    ? "Source account " + context.sourceAccount().getAccountId() + " is blacklisted"
                    : "Target account " + context.event().targetAccountId() + " is blacklisted";

            return Optional.of(new RuleViolationDetail(CODE, getCategory(), cfg.weight(), reason));
        });
    }
}
