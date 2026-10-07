package com.smartpay.controller;

import com.smartpay.dto.TransactionResponse;
import com.smartpay.dto.TransferRequest;
import com.smartpay.service.TransferResult;
import com.smartpay.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Transfers", description = "Atomic, idempotent wallet-to-wallet transfers (simulated money).")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    // 201 = new transfer, 200 = same Idempotency-Key repeated (old result returned)
    @Operation(summary = "Transfer money to another user (idempotent)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New transfer completed",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "200", description = "Same Idempotency-Key and request repeated: original result returned",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request, missing Idempotency-Key, or transfer to yourself"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
            @ApiResponse(responseCode = "403", description = "Sender or receiver account is frozen"),
            @ApiResponse(responseCode = "404", description = "Receiver not found"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key already used for a different request"),
            @ApiResponse(responseCode = "422", description = "Insufficient balance")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            Authentication authentication,
            @Parameter(required = true,
                    description = "Unique key for this transfer (max 100 characters). Repeating the same key with the same request returns the original result and moves no money.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        Long senderUserId = Long.valueOf(authentication.getName());
        TransferResult result = transferService.transfer(senderUserId, request, idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(result.transaction()));
    }
}