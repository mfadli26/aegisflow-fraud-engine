package com.aegisflow.fraud.stream;

import com.aegisflow.common.constant.KafkaTopics;
import com.aegisflow.common.enums.DecisionStatus;
import com.aegisflow.common.enums.RiskLevel;
import com.aegisflow.common.enums.TransactionType;
import com.aegisflow.common.event.RiskScoredEvent;
import com.aegisflow.common.event.TransactionCreatedEvent;
import com.aegisflow.fraud.dto.EnrichedTransaction;
import com.aegisflow.fraud.service.RiskScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionTopologyTest {

    @TempDir
    Path stateDir;

    @Mock
    private RiskScoringService riskScoringService;

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, TransactionCreatedEvent> incomingTopic;
    private TestOutputTopic<String, RiskScoredEvent> scoredTopic;
    private TestOutputTopic<String, RiskScoredEvent> alertsTopic;
    private TestOutputTopic<String, String> dltTopic;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        StreamsBuilder builder = new StreamsBuilder();
        FraudDetectionTopology topologyConfig = new FraudDetectionTopology(riskScoringService, mapper);
        topologyConfig.fraudStreamTopology(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "aegisflow-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toAbsolutePath().toString());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        testDriver = new TopologyTestDriver(builder.build(), props);

        JsonSerde<TransactionCreatedEvent> txSerde = jsonSerde(TransactionCreatedEvent.class, mapper);
        JsonSerde<RiskScoredEvent> scoredSerde = jsonSerde(RiskScoredEvent.class, mapper);

        incomingTopic = testDriver.createInputTopic(
                KafkaTopics.TX_INCOMING, Serdes.String().serializer(), txSerde.serializer());
        scoredTopic = testDriver.createOutputTopic(
                KafkaTopics.TX_SCORED, Serdes.String().deserializer(), scoredSerde.deserializer());
        alertsTopic = testDriver.createOutputTopic(
                KafkaTopics.TX_ALERTS, Serdes.String().deserializer(), scoredSerde.deserializer());
        dltTopic = testDriver.createOutputTopic(
                KafkaTopics.TX_DLT, Serdes.String().deserializer(), Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void shouldAccumulateWindowVelocityAndBranchAlertsCorrectly() {
        when(riskScoringService.evaluateAndPersist(any(EnrichedTransaction.class))).thenAnswer(inv -> {
            EnrichedTransaction enriched = inv.getArgument(0);
            int score = enriched.windowTxCount1m() >= 2 ? 85 : 10;
            RiskLevel level = RiskLevel.fromScore(score);
            DecisionStatus status = score >= 80 ? DecisionStatus.BLOCKED : DecisionStatus.APPROVED;
            return new RiskScoredEvent(
                    UUID.randomUUID(), enriched.event().txId(), enriched.event().sourceAccountId(),
                    null, enriched.event().amount(), "IDR", "Shop", "IDN", "Jakarta",
                    score, level, status, enriched.windowTxCount1m(), enriched.windowTxAmount1m(),
                    List.of(), 5L, Instant.now()
            );
        });

        Instant now = Instant.now();
        incomingTopic.pipeInput("ACC-1001", newTx("ACC-1001", new BigDecimal("100000"), now));
        incomingTopic.pipeInput("ACC-1001", newTx("ACC-1001", new BigDecimal("200000"), now.plusSeconds(5)));

        List<RiskScoredEvent> scored = scoredTopic.readValuesToList();
        List<RiskScoredEvent> alerts = alertsTopic.readValuesToList();

        assertThat(scored).hasSize(2);
        assertThat(scored.get(0).windowTxCount1m()).isEqualTo(1L);
        assertThat(scored.get(1).windowTxCount1m()).isEqualTo(2L);
        assertThat(scored.get(1).windowTxAmount1m()).isEqualByComparingTo("300000");
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(dltTopic.isEmpty()).isTrue();
    }

    @Test
    void shouldRouteProcessingExceptionsToDeadLetterTopic() {
        when(riskScoringService.evaluateAndPersist(any(EnrichedTransaction.class)))
                .thenThrow(new IllegalStateException("Source account missing"));

        incomingTopic.pipeInput("ACC-UNKNOWN", newTx("ACC-UNKNOWN", new BigDecimal("50000"), Instant.now()));

        assertThat(scoredTopic.isEmpty()).isTrue();
        assertThat(alertsTopic.isEmpty()).isTrue();
        assertThat(dltTopic.isEmpty()).isFalse();
        assertThat(dltTopic.readValue()).contains("Source account missing");
    }

    private TransactionCreatedEvent newTx(String accountId, BigDecimal amount, Instant ts) {
        return new TransactionCreatedEvent(
                UUID.randomUUID(), accountId, null, amount, "IDR", TransactionType.PAYMENT,
                "Test Merchant", "FNB", "IDN", "Jakarta", -6.2, 106.8, "DEV-1", "10.0.0.1", ts
        );
    }

    private <T> JsonSerde<T> jsonSerde(Class<T> type, ObjectMapper mapper) {
        JsonSerde<T> serde = new JsonSerde<>(type, mapper);
        serde.configure(Map.of(
                JsonDeserializer.TRUSTED_PACKAGES, "com.aegisflow.*",
                JsonDeserializer.USE_TYPE_INFO_HEADERS, false,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false
        ), false);
        return serde;
    }
}