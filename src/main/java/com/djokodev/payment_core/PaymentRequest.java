package com.djokodev.payment_core;

import java.math.BigDecimal;

public record PaymentRequest(
        String fromAccountReference,
        String toAccountReference,
        BigDecimal amount
) {
}