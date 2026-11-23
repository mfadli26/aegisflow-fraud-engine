package com.aegisflow.fraud.grid;

import com.aegisflow.fraud.config.FraudEngineProperties;
import com.aegisflow.fraud.dto.AccountGeoSnapshot;
import com.aegisflow.fraud.dto.RuleConfigDto;
import com.aegisflow.fraud.dto.UpdateRuleRequest;
import com.aegisflow.fraud.entity.RiskRuleEntity;
import com.aegisflow.fraud.repository.RiskRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DynamicRuleGridService {

    private static final Logger log = LoggerFactory.getLogger(DynamicRuleGridService.class);
    private static final String RULES_GRID_KEY = "aegis:grid:risk-rules";
    private static final String GEO_GRID_KEY = "aegis:grid:account-last-geo";
    private static final String SLIDING_WINDOW_PREFIX = "aegis:velocity:zset:";

    private final RiskRuleRepository riskRuleRepository;
    private final StringRedisTemplate redisTemplate;
    private final FraudEngineProperties properties;
    private final RMap<String, RuleConfigDto> rulesGrid;
    private final RMap<String, AccountGeoSnapshot> geoGrid;

    public DynamicRuleGridService(
            RiskRuleRepository riskRuleRepository,
            RedissonClient redissonClient,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            FraudEngineProperties properties) {
        this.riskRuleRepository = riskRuleRepository;
        this.redisTemplate = redisTemplate;
        this.properties = properties;

        ObjectMapper gridMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.rulesGrid = redissonClient.getMap(
                RULES_GRID_KEY,
                new TypedJsonJacksonCodec(String.class, RuleConfigDto.class, gridMapper));
        this.geoGrid = redissonClient.getMap(
                GEO_GRID_KEY,
                new TypedJsonJacksonCodec(String.class, AccountGeoSnapshot.class, gridMapper));
    }

    @PostConstruct
    public void warmUpRulesGrid() {
        Map<String, RuleConfigDto> dbRules = riskRuleRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toMap(RuleConfigDto::ruleCode, dto -> dto));
        rulesGrid.putAll(dbRules);
        log.info("Warmed up Redisson Distributed Rule Grid with {} rules", dbRules.size());
    }

    public Map<String, RuleConfigDto> getAllRulesSnapshot() {
        if (rulesGrid.isEmpty()) {
            warmUpRulesGrid();
        }
        return rulesGrid.readAllMap();
    }

    public List<RuleConfigDto> listAllRules() {
        return getAllRulesSnapshot().values().stream()
                .sorted(Comparator.comparing(RuleConfigDto::ruleCode))
                .toList();
    }

    public Optional<RuleConfigDto> getRule(String ruleCode) {
        RuleConfigDto cached = rulesGrid.get(ruleCode);
        if (cached != null) {
            return Optional.of(cached);
        }
        return riskRuleRepository.findById(ruleCode).map(entity -> {
            RuleConfigDto dto = toDto(entity);
            rulesGrid.put(ruleCode, dto);
            return dto;
        });
    }

    @Transactional
    public RuleConfigDto updateRule(String ruleCode, UpdateRuleRequest request) {
        RiskRuleEntity entity = riskRuleRepository.findById(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("Risk rule not found: " + ruleCode));

        if (request.weight() != null) {
            entity.setWeight(request.weight());
        }
        if (request.thresholdValue() != null) {
            entity.setThresholdValue(request.thresholdValue());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        entity.setUpdatedAt(Instant.now());

        RuleConfigDto updated = toDto(riskRuleRepository.save(entity));
        rulesGrid.put(ruleCode, updated);
        log.info("Updated rule={} in PostgreSQL and Redisson Data Grid (weight={}, threshold={}, enabled={})",
                ruleCode, updated.weight(), updated.thresholdValue(), updated.enabled());
        return updated;
    }

    /**
     * Exact sliding window counter using Redis Sorted Set (ZSET).
     */
    public long recordAndCountSlidingWindow(String accountId, String txId, Instant eventTime) {
        String key = SLIDING_WINDOW_PREFIX + accountId;
        long nowMillis = eventTime.toEpochMilli();
        long cutoffMillis = nowMillis - (properties.slidingWindowSeconds() * 1000L);

        redisTemplate.opsForZSet().add(key, txId, nowMillis);
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoffMillis);
        redisTemplate.expire(key, Duration.ofSeconds(properties.slidingWindowSeconds() * 2));

        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 1L;
    }

    public Optional<AccountGeoSnapshot> getLastGeoSnapshot(String accountId) {
        return Optional.ofNullable(geoGrid.get(accountId));
    }

    public void updateLastGeoSnapshot(AccountGeoSnapshot snapshot) {
        geoGrid.put(snapshot.accountId(), snapshot);
    }

    private RuleConfigDto toDto(RiskRuleEntity entity) {
        return new RuleConfigDto(
                entity.getRuleCode(),
                entity.getRuleName(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getWeight(),
                entity.getThresholdValue(),
                entity.isEnabled(),
                entity.getUpdatedAt()
        );
    }
}
