package com.djokodev.payment_core;

public record PaymentWebhookRequest(
        String eventId,
        String paymentReference,
        PaymentProviderStatus status
) {
}