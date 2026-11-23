package com.aegisflow.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Column(name = "home_country", nullable = false, length = 3)
    private String homeCountry;

    @Column(name = "home_city", nullable = false, length = 80)
    private String homeCity;

    @Column(name = "daily_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "avg_tx_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal avgTxAmount;

    @Column(name = "is_blacklisted", nullable = false)
    private boolean blacklisted;

    @Column(name = "risk_tier", nullable = false, length = 20)
    private String riskTier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHomeCountry() { return homeCountry; }
    public void setHomeCountry(String homeCountry) { this.homeCountry = homeCountry; }
    public String getHomeCity() { return homeCity; }
    public void setHomeCity(String homeCity) { this.homeCity = homeCity; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
    public BigDecimal getAvgTxAmount() { return avgTxAmount; }
    public void setAvgTxAmount(BigDecimal avgTxAmount) { this.avgTxAmount = avgTxAmount; }
    public boolean isBlacklisted() { return blacklisted; }
    public void setBlacklisted(boolean blacklisted) { this.blacklisted = blacklisted; }
    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
