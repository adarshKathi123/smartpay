package com.smartpay.controller;

import com.smartpay.dto.TransactionResponse;
import com.smartpay.dto.TransferRequest;
import com.smartpay.service.TransferResult;
import com.smartpay.service.TransferService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    // 201 = new transfer, 200 = same Idempotency-Key repeated (old result returned)
    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        Long senderUserId = Long.valueOf(authentication.getName());
        TransferResult result = transferService.transfer(senderUserId, request, idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(result.transaction()));
    }
}