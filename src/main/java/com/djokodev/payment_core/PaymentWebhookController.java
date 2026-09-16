package com.djokodev.payment_core;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> processPaymentWebhook(
            @RequestBody PaymentWebhookRequest request
    ) {
        PaymentResponse response =
                paymentService.processWebhook(request);

        return ResponseEntity.ok(response);
    }
}