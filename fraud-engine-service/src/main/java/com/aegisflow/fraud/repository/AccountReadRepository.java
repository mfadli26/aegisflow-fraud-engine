package com.aegisflow.fraud.repository;

import com.aegisflow.fraud.entity.AccountReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountReadRepository extends JpaRepository<AccountReadEntity, String> {
}
