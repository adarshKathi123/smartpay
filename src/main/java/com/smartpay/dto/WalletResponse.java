package com.smartpay.dto;

import com.smartpay.entity.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletResponse(
        Long id,
        BigDecimal balance,
        String currency,
        LocalDateTime updatedAt
) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getUpdatedAt()
        );
    }
}