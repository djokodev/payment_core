package com.djokodev.payment_core;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FakeMobileMoneyProvider implements PaymentProvider {

    @Override
    public PaymentProviderResult initiatePayment(
            String paymentReference,
            BigDecimal amount
    ) {
        return new PaymentProviderResult(
                PaymentProviderStatus.SUCCESS
        );
    }
}