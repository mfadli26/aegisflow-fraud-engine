package com.aegisflow.transaction.repository;

import com.aegisflow.transaction.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM TransactionEntity t
            WHERE t.sourceAccountId = :accountId
              AND t.createdAt >= :since
              AND t.status <> 'BLOCKED'
            """)
    BigDecimal sumActiveAmountSince(@Param("accountId") String accountId, @Param("since") Instant since);
}
