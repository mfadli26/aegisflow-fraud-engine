package com.aegisflow.investigation.repository;

import com.aegisflow.investigation.dto.AccountRiskRankDto;
import com.aegisflow.investigation.dto.AnomalyZScoreDto;
import com.aegisflow.investigation.dto.CircularTransferDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class FraudAnalyticsNativeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FraudAnalyticsNativeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Advanced Native SQL #1: Recursive CTE to detect chronological circular money-laundering rings (A -> B -> C -> A).
     */
    public List<CircularTransferDto> findCircularTransfers(int lookbackHours, int maxHops) {
        String sql = """
                WITH RECURSIVE transfer_chain AS (
                    SELECT
                        t.source_account_id AS start_account,
                        t.target_account_id AS current_account,
                        ARRAY[t.source_account_id::text, t.target_account_id::text] AS path,
                        1 AS hop_count,
                        t.amount::numeric AS total_amount,
                        t.amount::numeric AS min_hop_amount,
                        t.created_at AS first_tx_at,
                        t.created_at AS last_tx_at,
                        FALSE AS is_cycle
                    FROM transactions t
                    WHERE t.target_account_id IS NOT NULL
                      AND t.source_account_id <> t.target_account_id
                      AND t.created_at >= NOW() - MAKE_INTERVAL(hours => :lookbackHours)

                    UNION ALL

                    SELECT
                        tc.start_account,
                        t.target_account_id AS current_account,
                        tc.path || t.target_account_id::text,
                        tc.hop_count + 1,
                        tc.total_amount + t.amount,
                        LEAST(tc.min_hop_amount, t.amount),
                        tc.first_tx_at,
                        t.created_at,
                        (t.target_account_id = tc.start_account) AS is_cycle
                    FROM transfer_chain tc
                    JOIN transactions t
                      ON t.source_account_id = tc.current_account
                    WHERE tc.is_cycle = FALSE
                      AND tc.hop_count < :maxHops
                      AND t.target_account_id IS NOT NULL
                      AND t.created_at >= tc.last_tx_at
                      AND t.created_at <= tc.first_tx_at + MAKE_INTERVAL(hours => :lookbackHours)
                      AND (
                          t.target_account_id = tc.start_account
                          OR NOT (t.target_account_id::text = ANY(tc.path))
                      )
                )
                SELECT
                    start_account,
                    array_to_string(path, ' -> ') AS cycle_path,
                    hop_count,
                    total_amount,
                    min_hop_amount,
                    EXTRACT(EPOCH FROM (last_tx_at - first_tx_at))::bigint AS duration_seconds,
                    first_tx_at,
                    last_tx_at
                FROM transfer_chain
                WHERE is_cycle = TRUE
                  AND hop_count >= 2
                ORDER BY total_amount DESC, hop_count ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lookbackHours", lookbackHours)
                .addValue("maxHops", maxHops);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new CircularTransferDto(
                rs.getString("start_account"),
                rs.getString("cycle_path"),
                rs.getInt("hop_count"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("min_hop_amount"),
                rs.getLong("duration_seconds"),
                rs.getTimestamp("first_tx_at").toInstant(),
                rs.getTimestamp("last_tx_at").toInstant()
        ));
    }

    /**
     * Advanced Native SQL #2: Window Functions (AVG/STDDEV_POP OVER, LAG, Running SUM, DENSE_RANK) for Z-Score anomalies.
     */
    public List<AnomalyZScoreDto> findStatisticalAmountAnomalies(int lookbackDays, double minZScore, int limit) {
        String sql = """
                WITH tx_stats AS (
                    SELECT
                        t.tx_id,
                        t.source_account_id,
                        a.holder_name,
                        t.amount,
                        t.merchant_name,
                        t.city,
                        t.country_code,
                        t.status,
                        t.created_at,
                        ROUND(AVG(t.amount) OVER (PARTITION BY t.source_account_id), 2) AS account_avg_amount,
                        ROUND(COALESCE(STDDEV_POP(t.amount) OVER (PARTITION BY t.source_account_id), 0), 2) AS account_stddev_amount,
                        EXTRACT(EPOCH FROM (
                            t.created_at - LAG(t.created_at) OVER (PARTITION BY t.source_account_id ORDER BY t.created_at)
                        ))::bigint AS seconds_since_prev_tx,
                        SUM(t.amount) OVER (
                            PARTITION BY t.source_account_id
                            ORDER BY t.created_at
                            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                        ) AS cumulative_account_spend
                    FROM transactions t
                    JOIN accounts a ON a.account_id = t.source_account_id
                    WHERE t.created_at >= NOW() - MAKE_INTERVAL(days => :lookbackDays)
                ),
                scored_anomalies AS (
                    SELECT
                        *,
                        CASE
                            WHEN account_stddev_amount > 0
                                THEN ROUND(((amount - account_avg_amount) / account_stddev_amount)::numeric, 2)
                            ELSE 0.00
                        END AS z_score
                    FROM tx_stats
                )
                SELECT
                    DENSE_RANK() OVER (ORDER BY z_score DESC, amount DESC) AS anomaly_rank,
                    tx_id,
                    source_account_id,
                    holder_name,
                    amount,
                    account_avg_amount,
                    account_stddev_amount,
                    z_score,
                    seconds_since_prev_tx,
                    cumulative_account_spend,
                    merchant_name,
                    city,
                    country_code,
                    status,
                    created_at
                FROM scored_anomalies
                WHERE z_score >= :minZScore
                ORDER BY anomaly_rank ASC, created_at DESC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lookbackDays", lookbackDays)
                .addValue("minZScore", minZScore)
                .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            long prevSecRaw = rs.getLong("seconds_since_prev_tx");
            Long secondsSincePrev = rs.wasNull() ? null : prevSecRaw;
            return new AnomalyZScoreDto(
                    rs.getInt("anomaly_rank"),
                    rs.getObject("tx_id", UUID.class),
                    rs.getString("source_account_id"),
                    rs.getString("holder_name"),
                    rs.getBigDecimal("amount"),
                    rs.getBigDecimal("account_avg_amount"),
                    rs.getBigDecimal("account_stddev_amount"),
                    rs.getBigDecimal("z_score"),
                    secondsSincePrev,
                    rs.getBigDecimal("cumulative_account_spend"),
                    rs.getString("merchant_name"),
                    rs.getString("city"),
                    rs.getString("country_code"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant()
            );
        });
    }

    /**
     * Advanced Native SQL #3: Risk leaderboard using FILTER (WHERE ...), PERCENTILE_CONT(0.95), and DENSE_RANK().
     */
    public List<AccountRiskRankDto> findAccountRiskLeaderboard() {
        String sql = """
                SELECT
                    DENSE_RANK() OVER (ORDER BY AVG(fd.risk_score) DESC, COUNT(*) DESC) AS risk_rank,
                    a.account_id,
                    a.holder_name,
                    a.risk_tier,
                    a.is_blacklisted,
                    COUNT(fd.decision_id) AS total_evaluated,
                    COUNT(*) FILTER (WHERE fd.decision_status = 'BLOCKED') AS blocked_count,
                    COUNT(*) FILTER (WHERE fd.decision_status = 'REVIEW_REQUIRED') AS review_count,
                    ROUND(AVG(fd.risk_score)::numeric, 2) AS avg_risk_score,
                    MAX(fd.risk_score) AS max_risk_score,
                    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY fd.evaluation_latency_ms)::numeric, 2) AS p95_latency_ms,
                    MAX(fd.evaluated_at) AS last_evaluated_at
                FROM fraud_decisions fd
                JOIN accounts a ON a.account_id = fd.account_id
                GROUP BY a.account_id, a.holder_name, a.risk_tier, a.is_blacklisted
                ORDER BY risk_rank ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp lastEval = rs.getTimestamp("last_evaluated_at");
            return new AccountRiskRankDto(
                    rs.getInt("risk_rank"),
                    rs.getString("account_id"),
                    rs.getString("holder_name"),
                    rs.getString("risk_tier"),
                    rs.getBoolean("is_blacklisted"),
                    rs.getLong("total_evaluated"),
                    rs.getLong("blocked_count"),
                    rs.getLong("review_count"),
                    rs.getBigDecimal("avg_risk_score"),
                    rs.getInt("max_risk_score"),
                    rs.getBigDecimal("p95_latency_ms"),
                    lastEval != null ? lastEval.toInstant() : null
            );
        });
    }

    /**
     * Returns live PostgreSQL EXPLAIN ANALYZE plan to prove partial/composite index usage.
     */
    public List<String> explainHighRiskDecisionQuery() {
        String sql = """
                EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                SELECT decision_id, tx_id, account_id, risk_score, risk_level, evaluated_at
                FROM fraud_decisions
                WHERE risk_level IN ('HIGH', 'CRITICAL')
                ORDER BY evaluated_at DESC
                LIMIT 20
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
    }
}