package com.djokodev.payment_core;

import java.math.BigDecimal;

public interface PaymentProvider {

    PaymentProviderResult initiatePayment(
            String paymentReference,
            BigDecimal amount
    );
}