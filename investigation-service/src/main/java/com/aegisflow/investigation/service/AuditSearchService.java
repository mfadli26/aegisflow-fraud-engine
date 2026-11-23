package com.aegisflow.investigation.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.aegisflow.investigation.document.FraudAuditDocument;
import com.aegisflow.investigation.dto.AuditSearchResponseDto;
import com.aegisflow.investigation.dto.BucketCountDto;
import com.aegisflow.investigation.dto.FraudAnalyticsSummaryDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public AuditSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public AuditSearchResponseDto searchAuditLogs(
            String accountId,
            String riskLevel,
            String decisionStatus,
            Integer minScore,
            String keyword,
            int page,
            int size) {

        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (accountId != null && !accountId.isBlank()) {
            bool.filter(f -> f.term(t -> t.field("sourceAccountId").value(accountId)));
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            bool.filter(f -> f.term(t -> t.field("riskLevel").value(riskLevel.toUpperCase())));
        }
        if (decisionStatus != null && !decisionStatus.isBlank()) {
            bool.filter(f -> f.term(t -> t.field("decisionStatus").value(decisionStatus.toUpperCase())));
        }
        if (minScore != null) {
            bool.filter(f -> f.range(r -> r.number(n -> n.field("riskScore").gte(minScore.doubleValue()))));
        }
        if (keyword != null && !keyword.isBlank()) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("merchantName^2", "violationSummary")
                    .fuzziness("AUTO")
            ));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(bool.build())))
                .withPageable(PageRequest.of(page, size))
                .withSort(Sort.by(Sort.Direction.DESC, "evaluatedAt"))
                .build();

        SearchHits<FraudAuditDocument> hits = elasticsearchOperations.search(query, FraudAuditDocument.class);
        List<FraudAuditDocument> docs = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        return new AuditSearchResponseDto(hits.getTotalHits(), page, size, docs);
    }

    public FraudAnalyticsSummaryDto getRealtimeAggregations() {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.matchAll(m -> m)))
                .withMaxResults(0)
                .withAggregation("avg_risk_score", Aggregation.of(a -> a.avg(avg -> avg.field("riskScore"))))
                .withAggregation("by_risk_level", Aggregation.of(a -> a.terms(t -> t.field("riskLevel").size(10))))
                .withAggregation("by_decision", Aggregation.of(a -> a.terms(t -> t.field("decisionStatus").size(10))))
                .withAggregation("top_rules", Aggregation.of(a -> a.terms(t -> t.field("triggeredRules").size(10))))
                .withAggregation("top_accounts", Aggregation.of(a -> a.terms(t -> t.field("sourceAccountId").size(10))))
                .withAggregation("hourly_trend", Aggregation.of(a -> a.dateHistogram(dh -> dh
                        .field("evaluatedAt")
                        .calendarInterval(CalendarInterval.Hour)
                )))
                .build();

        SearchHits<FraudAuditDocument> hits = elasticsearchOperations.search(query, FraudAuditDocument.class);
        ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();

        if (aggs == null) {
            return new FraudAnalyticsSummaryDto(0L, 0.0, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        double avgScore = 0.0;
        ElasticsearchAggregation avgAgg = aggs.get("avg_risk_score");
        if (avgAgg != null) {
            double raw = avgAgg.aggregation().getAggregate().avg().value();
            if (Double.isFinite(raw)) {
                avgScore = Math.round(raw * 100.0) / 100.0;
            }
        }

        return new FraudAnalyticsSummaryDto(
                hits.getTotalHits(),
                avgScore,
                extractTerms(aggs, "by_risk_level"),
                extractTerms(aggs, "by_decision"),
                extractTerms(aggs, "top_rules"),
                extractTerms(aggs, "top_accounts"),
                extractHourly(aggs, "hourly_trend")
        );
    }

    private List<BucketCountDto> extractTerms(ElasticsearchAggregations aggs, String name) {
        ElasticsearchAggregation agg = aggs.get(name);
        if (agg == null) {
            return List.of();
        }
        return agg.aggregation().getAggregate().sterms().buckets().array().stream()
                .map(b -> new BucketCountDto(b.key().stringValue(), b.docCount()))
                .toList();
    }

    private List<BucketCountDto> extractHourly(ElasticsearchAggregations aggs, String name) {
        ElasticsearchAggregation agg = aggs.get(name);
        if (agg == null) {
            return List.of();
        }
        return agg.aggregation().getAggregate().dateHistogram().buckets().array().stream()
                .map(b -> new BucketCountDto(b.keyAsString(), b.docCount()))
                .toList();
    }
}