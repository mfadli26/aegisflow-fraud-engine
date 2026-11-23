package com.aegisflow.transaction.service;

import com.aegisflow.transaction.config.RedisConfig;
import com.aegisflow.transaction.dto.AccountProfileDto;
import com.aegisflow.transaction.dto.UpdateAccountRequest;
import com.aegisflow.transaction.entity.AccountEntity;
import com.aegisflow.transaction.exception.ResourceNotFoundException;
import com.aegisflow.transaction.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AccountProfileService {

    private static final Logger log = LoggerFactory.getLogger(AccountProfileService.class);
    private final AccountRepository accountRepository;

    public AccountProfileService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Cacheable(value = RedisConfig.ACCOUNT_CACHE, key = "#accountId")
    @Transactional(readOnly = true)
    public AccountProfileDto getAccountProfile(String accountId) {
        log.info("Cache MISS for accountId={}, loading profile from PostgreSQL", accountId);
        AccountEntity entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        return toDto(entity);
    }

    @CacheEvict(value = RedisConfig.ACCOUNT_CACHE, key = "#accountId")
    @Transactional
    public AccountProfileDto updateAccountProfile(String accountId, UpdateAccountRequest request) {
        AccountEntity entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (request.dailyLimit() != null) {
            entity.setDailyLimit(request.dailyLimit());
        }
        if (request.blacklisted() != null) {
            entity.setBlacklisted(request.blacklisted());
        }
        if (request.riskTier() != null && !request.riskTier().isBlank()) {
            entity.setRiskTier(request.riskTier().toUpperCase());
        }
        entity.setUpdatedAt(Instant.now());

        AccountEntity saved = accountRepository.save(entity);
        log.info("Updated accountId={} and evicted Redis cache entry", accountId);
        return toDto(saved);
    }

    private AccountProfileDto toDto(AccountEntity entity) {
        return new AccountProfileDto(
                entity.getAccountId(),
                entity.getHolderName(),
                entity.getHomeCountry(),
                entity.getHomeCity(),
                entity.getDailyLimit(),
                entity.getAvgTxAmount(),
                entity.isBlacklisted(),
                entity.getRiskTier()
        );
    }
}
