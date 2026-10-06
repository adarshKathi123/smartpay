package com.smartpay.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = "100000.00", message = "Amount must not exceed 100000.00")
        @Digits(integer = 6, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount
) {
}