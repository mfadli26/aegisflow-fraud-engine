package com.aegisflow.investigation.repository;

import com.aegisflow.investigation.document.FraudAuditDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FraudAuditRepository extends ElasticsearchRepository<FraudAuditDocument, String> {
}