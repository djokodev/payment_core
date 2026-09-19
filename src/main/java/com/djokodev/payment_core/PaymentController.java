package com.djokodev.payment_core;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request
    ) {

        PaymentResponse response =
                paymentService.createPayment(
                        idempotencyKey,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reference}/settle")
    public ResponseEntity<PaymentResponse> settlePayment(
            @PathVariable String reference
    ) {

        PaymentResponse response =
                paymentService.settlePayment(reference);

        return ResponseEntity.ok(response);
    }
}