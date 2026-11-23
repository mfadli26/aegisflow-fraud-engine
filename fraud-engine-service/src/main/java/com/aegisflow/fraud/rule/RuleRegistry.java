package com.aegisflow.fraud.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(RuleRegistry.class);
    private final List<FraudRuleEvaluator> orderedEvaluators;

    /**
     * Spring IoC automatically injects all active FraudRuleEvaluator beans sorted by @Order.
     */
    public RuleRegistry(List<FraudRuleEvaluator> evaluators) {
        this.orderedEvaluators = List.copyOf(evaluators);
        log.info("Registered {} active FraudRuleEvaluator beans via Spring IoC: {}",
                orderedEvaluators.size(),
                orderedEvaluators.stream().map(FraudRuleEvaluator::getRuleCode).toList());
    }

    public List<FraudRuleEvaluator> getEvaluators() {
        return orderedEvaluators;
    }

    public Map<String, List<String>> getRegisteredRulesByCategory() {
        return orderedEvaluators.stream()
                .collect(Collectors.groupingBy(
                        FraudRuleEvaluator::getCategory,
                        Collectors.mapping(FraudRuleEvaluator::getRuleCode, Collectors.toList())
                ));
    }
}
