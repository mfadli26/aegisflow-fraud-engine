package com.aegisflow.fraud.stream;

import com.aegisflow.common.constant.KafkaTopics;
import com.aegisflow.common.enums.RiskLevel;
import com.aegisflow.common.event.RiskScoredEvent;
import com.aegisflow.common.event.TransactionCreatedEvent;
import com.aegisflow.fraud.dto.EnrichedTransaction;
import com.aegisflow.fraud.dto.ScoringOutcome;
import com.aegisflow.fraud.dto.VelocityAccumulator;
import com.aegisflow.fraud.service.RiskScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class FraudDetectionTopology {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionTopology.class);
    public static final String VELOCITY_WINDOW_STORE = "tx-velocity-1m-store";

    private final RiskScoringService riskScoringService;
    private final ObjectMapper streamMapper;

    public FraudDetectionTopology(RiskScoringService riskScoringService, ObjectMapper objectMapper) {
        this.riskScoringService = riskScoringService;
        this.streamMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public KStream<String, TransactionCreatedEvent> fraudStreamTopology(StreamsBuilder streamsBuilder) {
        JsonSerde<TransactionCreatedEvent> txSerde = configuredJsonSerde(TransactionCreatedEvent.class);
        JsonSerde<VelocityAccumulator> velocitySerde = configuredJsonSerde(VelocityAccumulator.class);
        JsonSerde<RiskScoredEvent> scoredSerde = configuredJsonSerde(RiskScoredEvent.class);

        // Register persistent RocksDB WindowStore (1m tumbling windows retained for 15m)
        StoreBuilder<WindowStore<String, VelocityAccumulator>> windowStoreBuilder = Stores.windowStoreBuilder(
                Stores.persistentWindowStore(
                        VELOCITY_WINDOW_STORE,
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(1),
                        false
                ),
                Serdes.String(),
                velocitySerde
        );
        streamsBuilder.addStateStore(windowStoreBuilder);

        KStream<String, TransactionCreatedEvent> incomingStream = streamsBuilder.stream(
                KafkaTopics.TX_INCOMING,
                Consumed.with(Serdes.String(), txSerde)
        );

        // Stateful 1-minute velocity enrichment inside Kafka Streams
        KStream<String, EnrichedTransaction> enrichedStream = incomingStream.processValues(
                () -> new VelocityEnrichmentProcessor(VELOCITY_WINDOW_STORE),
                VELOCITY_WINDOW_STORE
        );

        // Evaluate rules & isolate failures cleanly for Dead Letter Topic routing
        KStream<String, ScoringOutcome> outcomeStream = enrichedStream.mapValues((accountId, enriched) -> {
            try {
                RiskScoredEvent scored = riskScoringService.evaluateAndPersist(enriched);
                return ScoringOutcome.ok(scored);
            } catch (Exception ex) {
                log.error("Failed to score txId={} for account={}; routing to DLT",
                        enriched.event().txId(), accountId, ex);
                String dltPayload = String.format(
                        "{\"txId\":\"%s\",\"accountId\":\"%s\",\"error\":\"%s\"}",
                        enriched.event().txId(), accountId, ex.getMessage().replace("\"", "'"));
                return ScoringOutcome.failure(dltPayload);
            }
        });

        // Stream Branching: separate DLT failures from valid scored events
        Map<String, KStream<String, ScoringOutcome>> branches = outcomeStream
                .split(Named.as("fraud-route-"))
                .branch((key, outcome) -> !outcome.success(), Branched.as("dlt"))
                .defaultBranch(Branched.as("scored"));

        branches.get("fraud-route-dlt")
                .mapValues(ScoringOutcome::errorPayload)
                .to(KafkaTopics.TX_DLT, Produced.with(Serdes.String(), Serdes.String()));

        KStream<String, RiskScoredEvent> validScoredStream = branches.get("fraud-route-scored")
                .mapValues(ScoringOutcome::scoredEvent);

        // Publish all scored events to tx.scored
        validScoredStream.to(KafkaTopics.TX_SCORED, Produced.with(Serdes.String(), scoredSerde));

        // Filter HIGH and CRITICAL risk transactions to tx.alerts
        validScoredStream
                .filter((key, scored) -> scored.riskLevel() == RiskLevel.HIGH || scored.riskLevel() == RiskLevel.CRITICAL)
                .to(KafkaTopics.TX_ALERTS, Produced.with(Serdes.String(), scoredSerde));

        return incomingStream;
    }

    private <T> JsonSerde<T> configuredJsonSerde(Class<T> targetType) {
        JsonSerde<T> serde = new JsonSerde<>(targetType, streamMapper);
        serde.configure(Map.of(
                JsonDeserializer.TRUSTED_PACKAGES, "com.aegisflow.*",
                JsonDeserializer.USE_TYPE_INFO_HEADERS, false,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false
        ), false);
        return serde;
    }
}
