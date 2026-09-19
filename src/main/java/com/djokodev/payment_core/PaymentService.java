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
    private final WebhookEventRepository webhookEventRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            AccountRepository accountRepository,
            PaymentProvider paymentProvider,
            WebhookEventRepository webhookEventRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.paymentProvider = paymentProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
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
            fromAccount.debit(request.amount());
            toAccount.credit(request.amount());
            payment.markSuccess();

            ledgerEntryRepository.save(
                    new LedgerEntry(
                            fromAccount,
                            LedgerEntryType.DEBIT,
                            request.amount(),
                            payment.getReference()
                    )
            );

            ledgerEntryRepository.save(
                    new LedgerEntry(
                            toAccount,
                            LedgerEntryType.CREDIT,
                            request.amount(),
                            payment.getReference()
                    )
            );

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


    @Transactional
    public PaymentResponse processWebhook(PaymentWebhookRequest request) {

        if (request.eventId() == null || request.eventId().isBlank()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (request.paymentReference() == null
                || request.paymentReference().isBlank()) {
            throw new IllegalArgumentException(
                    "Payment reference is required"
            );
        }

        if (request.status() == null) {
            throw new IllegalArgumentException(
                    "Webhook status is required"
            );
        }

        int inserted = webhookEventRepository.insertIfAbsent(
                request.eventId()
        );

        if (inserted == 0) {
            Payment payment = paymentRepository
                    .findByReference(request.paymentReference())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Payment not found"
                            )
                    );

            return PaymentResponse.from(payment);
        }

        Payment payment = paymentRepository
                .findByReference(request.paymentReference())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found"
                        )
                );

        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.FAILED) {


            return PaymentResponse.from(payment);
        }

        if (request.status() == PaymentProviderStatus.SUCCESS) {

            Account fromAccount = accountRepository
                    .findByReferenceForUpdate(
                            payment.getFromAccountReference()
                    )
                    .orElseThrow(() ->
                            new AccountNotFoundException(
                                    payment.getFromAccountReference()
                            )
                    );

            Account toAccount = accountRepository
                    .findByReferenceForUpdate(
                            payment.getToAccountReference()
                    )
                    .orElseThrow(() ->
                            new AccountNotFoundException(
                                    payment.getToAccountReference()
                            )
                    );

            fromAccount.debit(payment.getAmount());
            toAccount.credit(payment.getAmount());

            payment.markSuccess();

            ledgerEntryRepository.save(
                    new LedgerEntry(
                            fromAccount,
                            LedgerEntryType.DEBIT,
                            payment.getAmount(),
                            payment.getReference()
                    )
            );

            ledgerEntryRepository.save(
                    new LedgerEntry(
                            toAccount,
                            LedgerEntryType.CREDIT,
                            payment.getAmount(),
                            payment.getReference()
                    )
            );

        } else if (request.status() == PaymentProviderStatus.FAILED) {

            payment.markFailed();
        }

        return PaymentResponse.from(payment);
    }
}