package com.smartpay.controller;

import com.smartpay.dto.DepositRequest;
import com.smartpay.dto.WalletResponse;
import com.smartpay.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Wallet", description = "Wallet balance and simulated deposits. All money is simulated.")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(summary = "Get the wallet (created automatically on first use)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current wallet"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    })
    @GetMapping
    public WalletResponse getWallet(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return WalletResponse.from(walletService.getWallet(userId));
    }

    @Operation(summary = "Deposit simulated money (recorded as a DEPOSIT transaction)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated wallet"),
            @ApiResponse(responseCode = "400", description = "Invalid amount (0.01 to 100000.00, at most 2 decimals)"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
            @ApiResponse(responseCode = "403", description = "Account is frozen")
    })
    @PostMapping("/deposit")
    public WalletResponse deposit(Authentication authentication,
                                  @Valid @RequestBody DepositRequest request) {
        Long userId = Long.valueOf(authentication.getName());
        return WalletResponse.from(walletService.deposit(userId, request.amount()));
    }
}