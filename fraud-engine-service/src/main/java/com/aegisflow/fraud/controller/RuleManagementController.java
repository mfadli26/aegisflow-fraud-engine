package com.aegisflow.fraud.controller;

import com.aegisflow.common.dto.ApiResponse;
import com.aegisflow.fraud.dto.RuleConfigDto;
import com.aegisflow.fraud.dto.UpdateRuleRequest;
import com.aegisflow.fraud.dto.VelocityAccumulator;
import com.aegisflow.fraud.grid.DynamicRuleGridService;
import com.aegisflow.fraud.rule.RuleRegistry;
import com.aegisflow.fraud.stream.FraudDetectionTopology;
import jakarta.validation.Valid;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RuleManagementController {

    private final DynamicRuleGridService ruleGridService;
    private final RuleRegistry ruleRegistry;
    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    public RuleManagementController(
            DynamicRuleGridService ruleGridService,
            RuleRegistry ruleRegistry,
            StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
        this.ruleGridService = ruleGridService;
        this.ruleRegistry = ruleRegistry;
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<RuleConfigDto>>> listRules() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Dynamic rules retrieved from Redisson Data Grid",
                ruleGridService.listAllRules()));
    }

@   GetMapping("/rules/registered-beans")
    public ResponseEntity<ApiResponse<Object>> listRegisteredBeans() {
        Map<String, List<String>> grouped = ruleRegistry.getRegisteredRulesByCategory();
        return ResponseEntity.ok(ApiResponse.ok(
                "Active Spring IoC FraudRuleEvaluator beans grouped by category",
                grouped));
    }

    @PutMapping("/rules/{ruleCode}")
    public ResponseEntity<ApiResponse<RuleConfigDto>> updateRule(
            @PathVariable String ruleCode,
            @Valid @RequestBody UpdateRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Rule updated in PostgreSQL and synced to Redisson Data Grid",
                ruleGridService.updateRule(ruleCode, request)));
    }

    @GetMapping("/stream/velocity/{accountId}")
    public ResponseEntity<ApiResponse<List<VelocityAccumulator>>> queryWindowVelocity(
            @PathVariable String accountId) {
        KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();
        if (kafkaStreams == null || kafkaStreams.state() != KafkaStreams.State.RUNNING) {
            return ResponseEntity.ok(ApiResponse.error("Kafka Streams state store is not yet ready"));
        }

        ReadOnlyWindowStore<String, VelocityAccumulator> windowStore = kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                        FraudDetectionTopology.VELOCITY_WINDOW_STORE,
                        QueryableStoreTypes.windowStore()
                )
        );

        Instant now = Instant.now();
        Instant from = now.minusSeconds(900);
        List<VelocityAccumulator> windows = new ArrayList<>();
        try (WindowStoreIterator<VelocityAccumulator> iterator = windowStore.fetch(accountId, from, now)) {
            while (iterator.hasNext()) {
                windows.add(iterator.next().value);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Kafka Streams 1m velocity windows retrieved via Interactive Query",
                windows));
    }
}