package com.aegisflow.investigation.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

@Document(indexName = "fraud_audit_logs")
public class FraudAuditDocument {

    @Id
    private String decisionId;

    @Field(type = FieldType.Keyword)
    private String txId;

    @Field(type = FieldType.Keyword)
    private String sourceAccountId;

    @Field(type = FieldType.Keyword)
    private String targetAccountId;

    @Field(type = FieldType.Double)
    private double amount;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Text)
    private String merchantName;

    @Field(type = FieldType.Keyword)
    private String countryCode;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Integer)
    private int riskScore;

    @Field(type = FieldType.Keyword)
    private String riskLevel;

    @Field(type = FieldType.Keyword)
    private String decisionStatus;

    @Field(type = FieldType.Long)
    private long windowTxCount1m;

    @Field(type = FieldType.Keyword)
    private List<String> triggeredRules;

    @Field(type = FieldType.Text)
    private String violationSummary;

    @Field(type = FieldType.Long)
    private long evaluationLatencyMs;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant evaluatedAt;

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }
    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    public String getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(String targetAccountId) { this.targetAccountId = targetAccountId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getDecisionStatus() { return decisionStatus; }
    public void setDecisionStatus(String decisionStatus) { this.decisionStatus = decisionStatus; }
    public long getWindowTxCount1m() { return windowTxCount1m; }
    public void setWindowTxCount1m(long windowTxCount1m) { this.windowTxCount1m = windowTxCount1m; }
    public List<String> getTriggeredRules() { return triggeredRules; }
    public void setTriggeredRules(List<String> triggeredRules) { this.triggeredRules = triggeredRules; }
    public String getViolationSummary() { return violationSummary; }
    public void setViolationSummary(String violationSummary) { this.violationSummary = violationSummary; }
    public long getEvaluationLatencyMs() { return evaluationLatencyMs; }
    public void setEvaluationLatencyMs(long evaluationLatencyMs) { this.evaluationLatencyMs = evaluationLatencyMs; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}