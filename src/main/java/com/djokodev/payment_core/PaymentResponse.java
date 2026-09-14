package com.djokodev.payment_core;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String reference,
        BigDecimal amount,
        String fromAccountReference,
        String toAccountReference,
        PaymentStatus status,
        LocalDateTime createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getReference(),
                payment.getAmount(),
                payment.getFromAccountReference(),
                payment.getToAccountReference(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}