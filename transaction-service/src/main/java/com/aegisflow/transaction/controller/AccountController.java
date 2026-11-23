package com.aegisflow.transaction.controller;

import com.aegisflow.common.dto.ApiResponse;
import com.aegisflow.transaction.dto.AccountProfileDto;
import com.aegisflow.transaction.dto.UpdateAccountRequest;
import com.aegisflow.transaction.service.AccountProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountProfileService accountProfileService;

    public AccountController(AccountProfileService accountProfileService) {
        this.accountProfileService = accountProfileService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountProfileDto>> getAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.ok("Account profile retrieved", accountProfileService.getAccountProfile(accountId)));
    }

    @PatchMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountProfileDto>> updateAccount(
            @PathVariable String accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account profile updated and cache evicted",
                accountProfileService.updateAccountProfile(accountId, request)));
    }
}
