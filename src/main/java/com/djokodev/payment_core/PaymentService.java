package com.djokodev.payment_core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final PaymentProvider paymentProvider;

    public PaymentService(
            PaymentRepository paymentRepository,
            AccountRepository accountRepository,
            PaymentProvider paymentProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public PaymentResponse createPayment(
            String idempotencyKey,
            PaymentRequest request
    ) {

        validateRequest(idempotencyKey, request);

        Optional<Payment> existingPayment =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {

            Payment payment = existingPayment.get();

            if (!hasSameParameters(payment, request)) {
                throw new IdempotencyKeyConflictException();
            }

            return PaymentResponse.from(payment);
        }

        Account fromAccount = accountRepository
                .findByReference(request.fromAccountReference())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                request.fromAccountReference()
                        )
                );

        Account toAccount = accountRepository
                .findByReference(request.toAccountReference())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                request.toAccountReference()
                        )
                );

        fromAccount.debit(request.amount());
        toAccount.credit(request.amount());

        Payment payment = new Payment(
                idempotencyKey,
                request.amount(),
                request.fromAccountReference(),
                request.toAccountReference()
        );

        PaymentProviderResult providerResult =
                paymentProvider.initiatePayment(
                        payment.getReference(),
                        payment.getAmount()
                );

        if (providerResult.status() == PaymentProviderStatus.SUCCESS) {
            payment.markSuccess();
        } else if (providerResult.status() == PaymentProviderStatus.FAILED) {
            payment.markFailed();
        }

        paymentRepository.save(payment);

        return PaymentResponse.from(payment);
    }

    private boolean hasSameParameters(
            Payment payment,
            PaymentRequest request
    ) {
        return payment.getAmount().compareTo(request.amount()) == 0
                && payment.getFromAccountReference()
                .equals(request.fromAccountReference())
                && payment.getToAccountReference()
                .equals(request.toAccountReference());
    }

    private void validateRequest(
            String idempotencyKey,
            PaymentRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key is required"
            );
        }

        if (request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        if (request.fromAccountReference() == null
                || request.toAccountReference() == null) {

            throw new IllegalArgumentException(
                    "Account references are required"
            );
        }

        if (request.fromAccountReference()
                .equals(request.toAccountReference())) {

            throw new IllegalArgumentException(
                    "Source and destination accounts must be different"
            );
        }
    }
}