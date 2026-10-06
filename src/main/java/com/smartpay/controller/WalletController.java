package com.smartpay.controller;

import com.smartpay.dto.DepositRequest;
import com.smartpay.dto.WalletResponse;
import com.smartpay.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public WalletResponse getWallet(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return WalletResponse.from(walletService.getWallet(userId));
    }

    @PostMapping("/deposit")
    public WalletResponse deposit(Authentication authentication,
                                  @Valid @RequestBody DepositRequest request) {
        Long userId = Long.valueOf(authentication.getName());
        return WalletResponse.from(walletService.deposit(userId, request.amount()));
    }
}