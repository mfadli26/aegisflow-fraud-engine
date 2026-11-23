package com.aegisflow.fraud.service;

import com.aegisflow.common.enums.DecisionStatus;
import com.aegisflow.common.enums.RiskLevel;
import com.aegisflow.common.enums.TransactionType;
import com.aegisflow.common.event.RiskScoredEvent;
import com.aegisflow.common.event.TransactionCreatedEvent;
import com.aegisflow.fraud.config.FraudEngineProperties;
import com.aegisflow.fraud.dto.AccountGeoSnapshot;
import com.aegisflow.fraud.dto.EnrichedTransaction;
import com.aegisflow.fraud.dto.RuleConfigDto;
import com.aegisflow.fraud.entity.AccountReadEntity;
import com.aegisflow.fraud.grid.DynamicRuleGridService;
import com.aegisflow.fraud.repository.AccountReadRepository;
import com.aegisflow.fraud.repository.FraudDecisionJdbcRepository;
import com.aegisflow.fraud.rule.RuleRegistry;
import com.aegisflow.fraud.rule.impl.AmountAnomalyRule;
import com.aegisflow.fraud.rule.impl.BlacklistAccountRule;
import com.aegisflow.fraud.rule.impl.GeoImpossibleTravelRule;
import com.aegisflow.fraud.rule.impl.VelocitySpikeRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock
    private DynamicRuleGridService ruleGridService;
    @Mock
    private AccountReadRepository accountReadRepository;
    @Mock
    private FraudDecisionJdbcRepository decisionJdbcRepository;

    private RiskScoringService riskScoringService;
    private Map<String, RuleConfigDto> defaultRules;

    @BeforeEach
    void setUp() {
        RuleRegistry registry = new RuleRegistry(List.of(
                new BlacklistAccountRule(),
                new VelocitySpikeRule(),
                new AmountAnomalyRule(),
                new GeoImpossibleTravelRule()
        ));
        FraudEngineProperties props = new FraudEngineProperties(80, 60, 60);
        riskScoringService = new RiskScoringService(
                registry, ruleGridService, accountReadRepository, decisionJdbcRepository, props);

        Instant now = Instant.now();
        defaultRules = Map.of(
                BlacklistAccountRule.CODE, new RuleConfigDto(BlacklistAccountRule.CODE, "Blacklist", "COMPLIANCE", "", 85, 1.0, true, now),
                VelocitySpikeRule.CODE, new RuleConfigDto(VelocitySpikeRule.CODE, "Velocity", "BEHAVIORAL", "", 35, 3.0, true, now),
                AmountAnomalyRule.CODE, new RuleConfigDto(AmountAnomalyRule.CODE, "Amount", "MONETARY", "", 30, 5.0, true, now),
                GeoImpossibleTravelRule.CODE, new RuleConfigDto(GeoImpossibleTravelRule.CODE, "Geo", "LOCATION", "", 35, 900.0, true, now)
        );
    }

    @Test
    void shouldApproveNormalTransactionWithZeroScore() {
        AccountReadEntity account = mockAccount("ACC-1001", new BigDecimal("750000.00"), false);
        when(accountReadRepository.findById("ACC-1001")).thenReturn(Optional.of(account));
        when(ruleGridService.recordAndCountSlidingWindow(anyString(), anyString(), any())).thenReturn(1L);
        when(ruleGridService.getLastGeoSnapshot("ACC-1001")).thenReturn(Optional.empty());
        when(ruleGridService.getAllRulesSnapshot()).thenReturn(defaultRules);

        TransactionCreatedEvent event = sampleTx("ACC-1001", null, new BigDecimal("500000.00"), "IDN", "Jakarta", -6.2088, 106.8456, Instant.now());
        RiskScoredEvent result = riskScoringService.evaluateAndPersist(new EnrichedTransaction(event, 1L, event.amount()));

        assertThat(result.riskScore()).isZero();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.decisionStatus()).isEqualTo(DecisionStatus.APPROVED);
        assertThat(result.violations()).isEmpty();
        verify(decisionJdbcRepository).persistDecisionAndUpdateTransaction(result);
    }

    @Test
    void shouldBlockTransactionTriggeringAmountAnomalyAndImpossibleTravel() {
        Instant now = Instant.now();
        AccountReadEntity account = mockAccount("ACC-1001", new BigDecimal("750000.00"), false);
        when(accountReadRepository.findById("ACC-1001")).thenReturn(Optional.of(account));
        when(ruleGridService.recordAndCountSlidingWindow(anyString(), anyString(), any())).thenReturn(1L);
        when(ruleGridService.getLastGeoSnapshot("ACC-1001")).thenReturn(Optional.of(
                new AccountGeoSnapshot("ACC-1001", "IDN", "Jakarta", -6.2088, 106.8456, now.minusSeconds(300))
        ));
        when(ruleGridService.getAllRulesSnapshot()).thenReturn(defaultRules);

        TransactionCreatedEvent event = sampleTx("ACC-1001", null, new BigDecimal("15000000.00"), "SGP", "Singapore", 1.3521, 103.8198, now);
        RiskScoredEvent result = riskScoringService.evaluateAndPersist(new EnrichedTransaction(event, 1L, event.amount()));

        assertThat(result.riskScore()).isEqualTo(80);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.decisionStatus()).isEqualTo(DecisionStatus.BLOCKED);
        assertThat(result.violations()).extracting(v -> v.ruleCode())
                .containsExactly("AMOUNT_ANOMALY", "GEO_IMPOSSIBLE_TRAVEL");
    }

    @Test
    void shouldCapRiskScoreAt100WhenMultipleSevereRulesTrigger() {
        AccountReadEntity source = mockAccount("ACC-1001", new BigDecimal("500000.00"), false);
        AccountReadEntity target = mockAccount("ACC-9999", new BigDecimal("500000.00"), true);
        when(accountReadRepository.findById("ACC-1001")).thenReturn(Optional.of(source));
        when(accountReadRepository.findById("ACC-9999")).thenReturn(Optional.of(target));
        when(ruleGridService.recordAndCountSlidingWindow(anyString(), anyString(), any())).thenReturn(6L);
        when(ruleGridService.getLastGeoSnapshot("ACC-1001")).thenReturn(Optional.empty());
        when(ruleGridService.getAllRulesSnapshot()).thenReturn(defaultRules);

        TransactionCreatedEvent event = sampleTx("ACC-1001", "ACC-9999", new BigDecimal("10000000.00"), "IDN", "Jakarta", -6.2088, 106.8456, Instant.now());
        RiskScoredEvent result = riskScoringService.evaluateAndPersist(new EnrichedTransaction(event, 6L, event.amount()));

        assertThat(result.riskScore()).isEqualTo(100);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.decisionStatus()).isEqualTo(DecisionStatus.BLOCKED);
        assertThat(result.violations()).hasSize(3);
    }

    private AccountReadEntity mockAccount(String id, BigDecimal avgAmount, boolean blacklisted) {
        AccountReadEntity entity = mock(AccountReadEntity.class);
        lenient().when(entity.getAccountId()).thenReturn(id);
        lenient().when(entity.getAvgTxAmount()).thenReturn(avgAmount);
        lenient().when(entity.isBlacklisted()).thenReturn(blacklisted);
        return entity;
    }

    private TransactionCreatedEvent sampleTx(
            String src, String dst, BigDecimal amount,
            String country, String city, double lat, double lon, Instant ts) {
        return new TransactionCreatedEvent(
                UUID.randomUUID(), src, dst, amount, "IDR", TransactionType.PAYMENT,
                "Test Merchant", "RETAIL", country, city, lat, lon, "DEV-1", "10.0.0.1", ts
        );
    }
}