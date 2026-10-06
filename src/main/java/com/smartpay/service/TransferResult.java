package com.smartpay.service;

import com.smartpay.entity.Transaction;

// replayed = true means the same Idempotency-Key was already used and we returned the old result
public record TransferResult(Transaction transaction, boolean replayed) {
}