package com.djokodev.payment_core;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String fromAccountReference;

    @Column(nullable = false)
    private String toAccountReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Payment() {
    }

    public Payment(
            String idempotencyKey,
            BigDecimal amount,
            String fromAccountReference,
            String toAccountReference
    ) {
        this.reference = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.fromAccountReference = fromAccountReference;
        this.toAccountReference = toAccountReference;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFromAccountReference() {
        return fromAccountReference;
    }

    public String getToAccountReference() {
        return toAccountReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markSettled() {
        if (this.status != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Only a successful payment can be settled"
            );
        }

        this.status = PaymentStatus.SETTLED;
    }
}