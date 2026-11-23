package com.aegisflow.transaction.service;

import com.aegisflow.common.constant.KafkaTopics;
import com.aegisflow.common.event.TransactionCreatedEvent;
import com.aegisflow.transaction.dto.AccountProfileDto;
import com.aegisflow.transaction.dto.CreateTransactionRequest;
import com.aegisflow.transaction.dto.TransactionResponse;
import com.aegisflow.transaction.entity.TransactionEntity;
import com.aegisflow.transaction.exception.BusinessRuleException;
import com.aegisflow.transaction.exception.ConcurrencyConflictException;
import com.aegisflow.transaction.exception.ResourceNotFoundException;
import com.aegisflow.transaction.repository.TransactionRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestionService.class);
    private static final String ACCOUNT_LOCK_PREFIX = "aegis:lock:account:";

    private final AccountProfileService accountProfileService;
    private final IdempotencyService idempotencyService;
    private final TransactionRepository transactionRepository;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    public TransactionIngestionService(
            AccountProfileService accountProfileService,
            IdempotencyService idempotencyService,
            TransactionRepository transactionRepository,
            RedissonClient redissonClient,
            TransactionTemplate transactionTemplate,
            KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate) {
        this.accountProfileService = accountProfileService;
        this.idempotencyService = idempotencyService;
        this.transactionRepository = transactionRepository;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    public TransactionResponse ingestTransaction(String idempotencyKey, CreateTransactionRequest request) {
        // 1. Redis Idempotency Check (fast path for retries)
        if (!idempotencyService.tryClaim(idempotencyKey)) {
            return idempotencyService.getStoredResponse(idempotencyKey)
                    .orElseThrow(() -> new ConcurrencyConflictException("Duplicate request detected for key: " + idempotencyKey));
        }

        // Durable fallback check in PostgreSQL in case Redis entry expired after 24h
        Optional<TransactionEntity> existingDbTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingDbTx.isPresent()) {
            TransactionResponse replayed = toResponse(existingDbTx.get(), true);
            idempotencyService.markCompleted(idempotencyKey, replayed);
            return replayed;
        }

        String normalizedTarget = (request.targetAccountId() != null && !request.targetAccountId().isBlank())
                ? request.targetAccountId().trim()
                : null;

        // 2. Acquire Redisson Distributed Lock per source account to serialize daily-limit checks
        RLock lock = redissonClient.getLock(ACCOUNT_LOCK_PREFIX + request.sourceAccountId());
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 15, TimeUnit.SECONDS);
            if (!locked) {
                idempotencyService.release(idempotencyKey);
                throw new ConcurrencyConflictException("Account is busy processing another transaction: " + request.sourceAccountId());
            }

            // 3. Cache-Aside lookup for source (and target if transfer)
            AccountProfileDto sourceProfile = accountProfileService.getAccountProfile(request.sourceAccountId());
            if (normalizedTarget != null) {
                accountProfileService.getAccountProfile(normalizedTarget);
            }

            // 4. Execute DB validation & persist strictly INSIDE lock before unlocking
            TransactionEntity persisted = transactionTemplate.execute(status -> {
                Instant startOfWindow = Instant.now().minus(24, ChronoUnit.HOURS);
                BigDecimal spentLast24h = transactionRepository.sumActiveAmountSince(request.sourceAccountId(), startOfWindow);
                BigDecimal projectedTotal = spentLast24h.add(request.amount());

                if (projectedTotal.compareTo(sourceProfile.dailyLimit()) > 0) {
                    throw new BusinessRuleException(
                            "Daily limit exceeded for account " + request.sourceAccountId()
                                    + " (limit=" + sourceProfile.dailyLimit() + ", projected=" + projectedTotal + ")");
                }

                TransactionEntity entity = new TransactionEntity();
                entity.setTxId(UUID.randomUUID());
                entity.setIdempotencyKey(idempotencyKey);
                entity.setSourceAccountId(request.sourceAccountId());
                entity.setTargetAccountId(normalizedTarget);
                entity.setAmount(request.amount());
                entity.setCurrency(request.currency().toUpperCase());
                entity.setTxType(request.txType());
                entity.setMerchantName(request.merchantName());
                entity.setMerchantCategory(request.merchantCategory());
                entity.setCountryCode(request.countryCode().toUpperCase());
                entity.setCity(request.city());
                entity.setLatitude(request.latitude());
                entity.setLongitude(request.longitude());
                entity.setDeviceId(request.deviceId());
                entity.setIpAddress(request.ipAddress());
                entity.setStatus("PENDING");
                entity.setCreatedAt(Instant.now());
                return transactionRepository.save(entity);
            });

            if (persisted == null) {
                throw new IllegalStateException("Failed to persist transaction");
            }

            // 5. Publish event to Kafka keyed by sourceAccountId (guarantees per-account partition ordering)
            TransactionCreatedEvent event = new TransactionCreatedEvent(
                    persisted.getTxId(),
                    persisted.getSourceAccountId(),
                    persisted.getTargetAccountId(),
                    persisted.getAmount(),
                    persisted.getCurrency(),
                    persisted.getTxType(),
                    persisted.getMerchantName(),
                    persisted.getMerchantCategory(),
                    persisted.getCountryCode(),
                    persisted.getCity(),
                    persisted.getLatitude(),
                    persisted.getLongitude(),
                    persisted.getDeviceId(),
                    persisted.getIpAddress(),
                    persisted.getCreatedAt()
            );

            kafkaTemplate.send(KafkaTopics.TX_INCOMING, persisted.getSourceAccountId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish txId={} to Kafka topic {}", persisted.getTxId(), KafkaTopics.TX_INCOMING, ex);
                        } else {
                            log.info("Published txId={} to topic={} partition={} offset={}",
                                    persisted.getTxId(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });

            TransactionResponse response = toResponse(persisted, false);
            idempotencyService.markCompleted(idempotencyKey, response);
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            idempotencyService.release(idempotencyKey);
            throw new ConcurrencyConflictException("Interrupted while waiting for distributed account lock");
        } catch (RuntimeException e) {
            idempotencyService.release(idempotencyKey);
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public TransactionResponse getTransaction(UUID txId) {
        TransactionEntity entity = transactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
        return toResponse(entity, false);
    }

    private TransactionResponse toResponse(TransactionEntity entity, boolean replayed) {
        return new TransactionResponse(
                entity.getTxId(),
                entity.getIdempotencyKey(),
                entity.getSourceAccountId(),
                entity.getTargetAccountId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getTxType(),
                entity.getMerchantName(),
                entity.getCountryCode(),
                entity.getCity(),
                entity.getStatus(),
                replayed,
                entity.getCreatedAt()
        );
    }
}
