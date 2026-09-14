package com.djokodev.payment_core;

/**
 * Exception métier levée quand un compte n'a pas assez de fonds
 * pour honorer un débit. C'est une RuntimeException (non-checked)
 * afin que Spring déclenche automatiquement le rollback de la
 * transaction en cours (voir note plus bas sur ce comportement).
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
