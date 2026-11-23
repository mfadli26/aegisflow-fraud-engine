package com.aegisflow.investigation.config;

import com.aegisflow.common.event.RiskScoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, RiskScoredEvent> riskScoredConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            ObjectMapper objectMapper) {
        ObjectMapper kafkaMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JsonDeserializer<RiskScoredEvent> deserializer =
                new JsonDeserializer<>(RiskScoredEvent.class, kafkaMapper, false);
        deserializer.addTrustedPackages("com.aegisflow.*");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "aegisflow-investigation-es-indexer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RiskScoredEvent> riskScoredKafkaListenerContainerFactory(
            ConsumerFactory<String, RiskScoredEvent> riskScoredConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RiskScoredEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(riskScoredConsumerFactory);
        return factory;
    }
}