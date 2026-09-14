package com.djokodev.payment_core;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Représente un compte financier dans le système.
 *
 * Note fintech : le solde est stocké en BigDecimal, jamais en double/float.
 * Les types flottants introduisent des erreurs d'arrondi inacceptables
 * dès qu'on manipule de l'argent réel (ex: 0.1 + 0.2 != 0.3 en double).
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference; // identifiant métier lisible, ex: "ACC-AWA-001"

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    protected Account() {
        // Constructeur vide requis par JPA/Hibernate
    }

    public Account(String reference, BigDecimal balance) {
        this.reference = reference;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * Débite le compte. Lève une exception métier si le solde est insuffisant
     * plutôt que de laisser le solde devenir négatif silencieusement.
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant du débit doit être positif");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Solde insuffisant sur le compte " + reference
            );
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * Crédite le compte.
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant du crédit doit être positif");
        }
        this.balance = this.balance.add(amount);
    }
}
