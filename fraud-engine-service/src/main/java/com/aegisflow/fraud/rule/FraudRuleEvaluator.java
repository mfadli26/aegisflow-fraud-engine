package com.aegisflow.fraud.rule;

import com.aegisflow.common.event.RuleViolationDetail;

import java.util.Optional;

public interface FraudRuleEvaluator {
    String getRuleCode();
    String getCategory();
    Optional<RuleViolationDetail> evaluate(RuleEvaluationContext context);
}
