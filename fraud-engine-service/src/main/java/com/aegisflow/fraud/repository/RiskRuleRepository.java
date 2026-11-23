package com.aegisflow.fraud.repository;

import com.aegisflow.fraud.entity.RiskRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRuleRepository extends JpaRepository<RiskRuleEntity, String> {
}
