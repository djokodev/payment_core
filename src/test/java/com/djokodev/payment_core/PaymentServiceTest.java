package com.djokodev.payment_core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
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
}