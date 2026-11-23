package com.aegisflow.investigation.dto;

import com.aegisflow.investigation.document.FraudAuditDocument;

import java.util.List;

public record AuditSearchResponseDto(
        long totalHits,
        int page,
        int size,
        List<FraudAuditDocument> results
) {
}