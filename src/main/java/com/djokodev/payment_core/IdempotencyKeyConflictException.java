package com.djokodev.payment_core;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key has already been used with different parameters");
    }
}