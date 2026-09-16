package com.djokodev.payment_core;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FakeMobileMoneyProvider implements PaymentProvider {

    private PaymentProviderStatus status = PaymentProviderStatus.SUCCESS;

    public void setStatus(PaymentProviderStatus status) {
        this.status = status;
    }

    @Override
    public PaymentProviderResult initiatePayment(
            String paymentReference,
            BigDecimal amount
    ) {
        return new PaymentProviderResult(status);
    }
}