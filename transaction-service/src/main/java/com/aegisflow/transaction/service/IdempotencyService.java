package com.aegisflow.transaction.service;

import com.aegisflow.transaction.dto.TransactionResponse;
import com.aegisflow.transaction.exception.ConcurrencyConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final String KEY_PREFIX = "aegis:idempotency:";
    private static final String IN_PROGRESS_MARKER = "IN_PROGRESS";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(2);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean tryClaim(String idempotencyKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + idempotencyKey, IN_PROGRESS_MARKER, PROCESSING_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public Optional<TransactionResponse> getStoredResponse(String idempotencyKey) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (raw == null) {
            return Optional.empty();
        }
        if (IN_PROGRESS_MARKER.equals(raw)) {
            throw new ConcurrencyConflictException("Transaction with Idempotency-Key is currently being processed: " + idempotencyKey);
        }
        try {
            TransactionResponse cached = objectMapper.readValue(raw, TransactionResponse.class);
            return Optional.of(new TransactionResponse(
                    cached.txId(),
                    cached.idempotencyKey(),
                    cached.sourceAccountId(),
                    cached.targetAccountId(),
                    cached.amount(),
                    cached.currency(),
                    cached.txType(),
                    cached.merchantName(),
                    cached.countryCode(),
                    cached.city(),
                    cached.status(),
                    true,
                    cached.createdAt()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency cache payload", e);
        }
    }

    public void markCompleted(String idempotencyKey, TransactionResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, json, COMPLETED_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency cache payload", e);
        }
    }

    public void release(String idempotencyKey) {
        redisTemplate.delete(KEY_PREFIX + idempotencyKey);
    }
}
