package com.aegisflow.fraud.rule;

import com.aegisflow.common.event.TransactionCreatedEvent;
import com.aegisflow.fraud.dto.AccountGeoSnapshot;
import com.aegisflow.fraud.dto.RuleConfigDto;
import com.aegisflow.fraud.entity.AccountReadEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public record RuleEvaluationContext(
        TransactionCreatedEvent event,
        AccountReadEntity sourceAccount,
        Optional<AccountReadEntity> targetAccount,
        long kafkaWindowTxCount1m,
        BigDecimal kafkaWindowTxAmount1m,
        long redisSlidingWindowCount1m,
        Optional<AccountGeoSnapshot> lastGeoSnapshot,
        Map<String, RuleConfigDto> ruleConfigs
) {
    public Optional<RuleConfigDto> activeConfig(String ruleCode) {
        return Optional.ofNullable(ruleConfigs.get(ruleCode))
                .filter(RuleConfigDto::enabled);
    }
}
