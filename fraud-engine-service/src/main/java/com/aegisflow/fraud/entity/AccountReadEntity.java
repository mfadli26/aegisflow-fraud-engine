package com.aegisflow.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class AccountReadEntity {

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

    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public String getHomeCountry() { return homeCountry; }
    public String getHomeCity() { return homeCity; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public BigDecimal getAvgTxAmount() { return avgTxAmount; }
    public boolean isBlacklisted() { return blacklisted; }
    public String getRiskTier() { return riskTier; }
}
