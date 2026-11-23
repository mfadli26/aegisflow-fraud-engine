package com.aegisflow.transaction.controller;

import com.aegisflow.common.dto.ApiResponse;
import com.aegisflow.transaction.dto.CreateTransactionRequest;
import com.aegisflow.transaction.dto.TransactionResponse;
import com.aegisflow.transaction.service.TransactionIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionIngestionService ingestionService;

    public TransactionController(TransactionIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = ingestionService.ingestTransaction(idempotencyKey, request);
        HttpStatus status = response.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        String msg = response.replayed()
                ? "Idempotent replay returned cached transaction"
                : "Transaction accepted and published for real-time fraud scoring";
        return ResponseEntity.status(status).body(ApiResponse.ok(msg, response));
    }

    @GetMapping("/{txId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable UUID txId) {
        return ResponseEntity.ok(ApiResponse.ok("Transaction retrieved", ingestionService.getTransaction(txId)));
    }
}
