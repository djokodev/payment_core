package com.djokodev.payment_core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FakeMobileMoneyProvider paymentProvider;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;


    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        webhookEventRepository.deleteAll();

        ledgerEntryRepository.deleteAll();
        ledgerEntryRepository.flush();

        accountRepository.deleteAll();

        Account sourceAccount = new Account(
                "ACC-SOURCE",
                new BigDecimal("10000")
        );

        Account destinationAccount = new Account(
                "ACC-DESTINATION",
                new BigDecimal("5000")
        );

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        paymentProvider.setStatus(PaymentProviderStatus.SUCCESS);
    }

    @Test
    void shouldProcessNewPayment() {

        PaymentRequest request = new PaymentRequest(
                "ACC-SOURCE",
                "ACC-DESTINATION",
                new BigDecimal("3000")
        );

        PaymentResponse response =
                paymentService.createPayment("KEY-001", request);

        assertNotNull(response);
        assertEquals(new BigDecimal("3000"), response.amount());
        assertEquals(PaymentStatus.SUCCESS, response.status());

        Account source =
                accountRepository
                        .findByReference("ACC-SOURCE")
                        .orElseThrow();

        Account destination =
                accountRepository
                        .findByReference("ACC-DESTINATION")
                        .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("7000").compareTo(source.getBalance())
        );
        assertEquals(
                0,
                new BigDecimal("8000").compareTo(destination.getBalance())
        );

        assertEquals(1, paymentRepository.count());

        assertEquals(2, ledgerEntryRepository.count());

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();

        LedgerEntry debitEntry = entries.stream()
                .filter(entry -> entry.getType() == LedgerEntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        LedgerEntry creditEntry = entries.stream()
                .filter(entry -> entry.getType() == LedgerEntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("3000").compareTo(debitEntry.getAmount())
        );

        assertEquals(
                0,
                new BigDecimal("3000").compareTo(creditEntry.getAmount())
        );

        assertEquals(
                "ACC-SOURCE",
                debitEntry.getAccount().getReference()
        );

        assertEquals(
                "ACC-DESTINATION",
                creditEntry.getAccount().getReference()
        );

        assertEquals(
                response.reference(),
                debitEntry.getTransactionReference()
        );

        assertEquals(
                response.reference(),
                creditEntry.getTransactionReference()
        );
    }

    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() {

        PaymentRequest request = new PaymentRequest(
                "ACC-SOURCE",
                "ACC-DESTINATION",
                new BigDecimal("3000")
        );

        PaymentResponse firstResponse =
                paymentService.createPayment(
                        "KEY-002",
                        request
                );

        PaymentResponse secondResponse =
                paymentService.createPayment(
                        "KEY-002",
                        request
                );

        assertEquals(
                firstResponse.reference(),
                secondResponse.reference()
        );

        Account source =
                accountRepository
                        .findByReference("ACC-SOURCE")
                        .orElseThrow();

        Account destination =
                accountRepository
                        .findByReference("ACC-DESTINATION")
                        .orElseThrow();

        // Le deuxième appel ne doit pas débiter à nouveau.
        assertEquals(
                0,
                new BigDecimal("7000").compareTo(source.getBalance())
        );
        assertEquals(
                0,
                new BigDecimal("8000").compareTo(destination.getBalance())
        );

        // Une seule opération Payment doit exister.
        assertEquals(1, paymentRepository.count());
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentParameters() {

        PaymentRequest firstRequest = new PaymentRequest(
                "ACC-SOURCE",
                "ACC-DESTINATION",
                new BigDecimal("3000")
        );

        PaymentRequest secondRequest = new PaymentRequest(
                "ACC-SOURCE",
                "ACC-DESTINATION",
                new BigDecimal("5000")
        );

        paymentService.createPayment(
                "KEY-003",
                firstRequest
        );

        assertThrows(
                IdempotencyKeyConflictException.class,
                () -> paymentService.createPayment(
                        "KEY-003",
                        secondRequest
                )
        );

        Account source =
                accountRepository
                        .findByReference("ACC-SOURCE")
                        .orElseThrow();

        Account destination =
                accountRepository
                        .findByReference("ACC-DESTINATION")
                        .orElseThrow();

        // Le deuxième appel ne doit avoir aucun effet financier.
        assertEquals(
                0,
                new BigDecimal("7000").compareTo(source.getBalance())
        );
        assertEquals(
                0,
                new BigDecimal("8000").compareTo(destination.getBalance())
        );

        assertEquals(1, paymentRepository.count());
    }


    @Test
    void shouldNotMoveBalancesWhenProviderFails() {

        paymentProvider.setStatus(PaymentProviderStatus.FAILED);

        Account source = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destination = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        BigDecimal sourceBalanceBefore = source.getBalance();
        BigDecimal destinationBalanceBefore = destination.getBalance();

        PaymentResponse response = paymentService.createPayment(
                "idem-failed-001",
                new PaymentRequest(
                        "ACC-SOURCE",
                        "ACC-DESTINATION",
                        new BigDecimal("3000")
                )
        );

        Account sourceAfter = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destinationAfter = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        assertEquals(PaymentStatus.FAILED, response.status());

        assertEquals(
                sourceBalanceBefore,
                sourceAfter.getBalance()
        );

        assertEquals(
                destinationBalanceBefore,
                destinationAfter.getBalance()
        );
    }


    @Test
    void shouldKeepPaymentPendingWhenProviderTimesOut() {

        paymentProvider.setStatus(PaymentProviderStatus.TIMEOUT);

        Account source = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destination = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        BigDecimal sourceBalanceBefore = source.getBalance();
        BigDecimal destinationBalanceBefore = destination.getBalance();

        PaymentResponse response = paymentService.createPayment(
                "idem-timeout-001",
                new PaymentRequest(
                        "ACC-SOURCE",
                        "ACC-DESTINATION",
                        new BigDecimal("3000")
                )
        );

        Account sourceAfter = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destinationAfter = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        assertEquals(PaymentStatus.PENDING, response.status());

        assertEquals(
                sourceBalanceBefore,
                sourceAfter.getBalance()
        );

        assertEquals(
                destinationBalanceBefore,
                destinationAfter.getBalance()
        );
    }


    @Test
    void shouldCompletePendingPaymentWhenWebhookSucceeds() {

        paymentProvider.setStatus(PaymentProviderStatus.TIMEOUT);

        PaymentResponse initialResponse = paymentService.createPayment(
                "KEY-WEBHOOK-001",
                new PaymentRequest(
                        "ACC-SOURCE",
                        "ACC-DESTINATION",
                        new BigDecimal("3000")
                )
        );

        assertEquals(PaymentStatus.PENDING, initialResponse.status());

        PaymentResponse webhookResponse = paymentService.processWebhook(
                new PaymentWebhookRequest(
                        "EVENT-001",
                        initialResponse.reference(),
                        PaymentProviderStatus.SUCCESS
                )
        );

        assertEquals(PaymentStatus.SUCCESS, webhookResponse.status());

        Account source = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destination = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("7000").compareTo(source.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("8000").compareTo(destination.getBalance())
        );
    }


    @Test
    void shouldFailPendingPaymentWhenWebhookFails() {

        paymentProvider.setStatus(PaymentProviderStatus.TIMEOUT);

        PaymentResponse initialResponse = paymentService.createPayment(
                "KEY-WEBHOOK-002",
                new PaymentRequest(
                        "ACC-SOURCE",
                        "ACC-DESTINATION",
                        new BigDecimal("3000")
                )
        );

        assertEquals(PaymentStatus.PENDING, initialResponse.status());

        PaymentResponse webhookResponse = paymentService.processWebhook(
                new PaymentWebhookRequest(
                        "EVENT-002",
                        initialResponse.reference(),
                        PaymentProviderStatus.FAILED
                )
        );

        assertEquals(PaymentStatus.FAILED, webhookResponse.status());

        Account source = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destination = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("10000").compareTo(source.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("5000").compareTo(destination.getBalance())
        );
    }


    @Test
    void shouldNotProcessSameWebhookTwice() {

        paymentProvider.setStatus(PaymentProviderStatus.TIMEOUT);

        PaymentResponse initialResponse = paymentService.createPayment(
                "KEY-WEBHOOK-003",
                new PaymentRequest(
                        "ACC-SOURCE",
                        "ACC-DESTINATION",
                        new BigDecimal("3000")
                )
        );

        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "EVENT-003",
                initialResponse.reference(),
                PaymentProviderStatus.SUCCESS
        );

        paymentService.processWebhook(webhook);

        paymentService.processWebhook(webhook);

        Account source = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destination = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("7000").compareTo(source.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("8000").compareTo(destination.getBalance())
        );

        assertEquals(1, webhookEventRepository.count());
    }


    @Test
    void shouldProcessSameWebhookOnlyOnceWhenReceivedConcurrently()
            throws InterruptedException {

        paymentProvider.setStatus(PaymentProviderStatus.TIMEOUT);

        PaymentResponse initialResponse = paymentService.createPayment(
                "KEY-WEBHOOK-CONCURRENT-001",
                new PaymentRequest(
                        "ACC-SOURCE",
                        "ACC-DESTINATION",
                        new BigDecimal("3000")
                )
        );

        assertEquals(PaymentStatus.PENDING, initialResponse.status());

        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "EVENT-CONCURRENT-001",
                initialResponse.reference(),
                PaymentProviderStatus.SUCCESS
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<Exception> exceptions = new ArrayList<>();

        Runnable task = () -> {
            try {
                startSignal.await();
                paymentService.processWebhook(webhook);
            } catch (Exception e) {
                synchronized (exceptions) {
                    exceptions.add(e);
                }
            }
        };

        executor.submit(task);
        executor.submit(task);

        startSignal.countDown();

        executor.shutdown();

        while (!executor.isTerminated()) {
            Thread.sleep(10);
        }

        assertTrue(
                exceptions.isEmpty(),
                "Aucune erreur ne devrait être provoquée par un webhook dupliqué"
        );

        Account source = accountRepository
                .findByReference("ACC-SOURCE")
                .orElseThrow();

        Account destination = accountRepository
                .findByReference("ACC-DESTINATION")
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("7000").compareTo(source.getBalance())
        );

        assertEquals(
                0,
                new BigDecimal("8000").compareTo(destination.getBalance())
        );

        assertEquals(1, webhookEventRepository.count());

        Payment payment = paymentRepository
                .findByReference(initialResponse.reference())
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }
}