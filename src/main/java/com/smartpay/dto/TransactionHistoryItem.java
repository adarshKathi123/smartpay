package com.smartpay.dto;

import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// direction is SENT or RECEIVED from the point of view of the logged-in user
public record TransactionHistoryItem(
        String referenceId,
        String direction,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        Long counterpartyUserId,
        LocalDateTime createdAt
) {
}