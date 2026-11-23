package com.aegisflow.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "risk_rules")
public class RiskRuleEntity {

    @Id
    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Column(name = "threshold_value", nullable = false)
    private double thresholdValue;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getRuleCode() { return ruleCode; }
    public String getRuleName() { return ruleName; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(double thresholdValue) { this.thresholdValue = thresholdValue; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
