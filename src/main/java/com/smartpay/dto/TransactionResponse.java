package com.smartpay.dto;

import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String referenceId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        Long senderWalletId,
        Long receiverWalletId,
        LocalDateTime createdAt
) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getReferenceId(),
                t.getType(),
                t.getStatus(),
                t.getAmount(),
                t.getSenderWalletId(),
                t.getReceiverWalletId(),
                t.getCreatedAt()
        );
    }
}