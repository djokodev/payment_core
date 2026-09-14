package com.djokodev.payment_core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Transfère un montant d'un compte source vers un compte destination.
     *
     * @Transactional garantit l'ATOMICITÉ de cette opération :
     * - Si tout se passe bien, les deux modifications (débit + crédit)
     *   sont validées ensemble (COMMIT).
     * - Si une exception RuntimeException est levée à n'importe quel
     *   moment dans cette méthode (ex: solde insuffisant, compte
     *   introuvable), Spring déclenche automatiquement un ROLLBACK :
     *   AUCUNE des deux modifications n'est appliquée.
     *
     * Point important (celui du Gate du Jour 4) :
     * Ce @Transactional protège uniquement les opérations sur NOTRE
     * base de données. Si cette méthode appelait un service externe
     * (ex: un provider Mobile Money), un échec de cet appel externe
     * ne serait PAS annulé par ce mécanisme — @Transactional n'a
     * aucune autorité sur un système en dehors de notre propre DB.
     */
    @Transactional
    public void transfer(String fromReference, String toReference, BigDecimal amount) {

        Account fromAccount = accountRepository.findByReference(fromReference)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Compte source introuvable : " + fromReference
                ));

        Account toAccount = accountRepository.findByReference(toReference)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Compte destination introuvable : " + toReference
                ));

        // Étape 1 : débit du compte source.
        // Si le solde est insuffisant, debit() lève une exception ici,
        // et grâce à @Transactional, rien n'est persisté en base.
        fromAccount.debit(amount);

        // Étape 2 : crédit du compte destination.
        // Si une erreur survient ICI (ex: contrainte DB violée),
        // le débit de l'étape 1 sera lui aussi annulé par le rollback,
        // exactement le scénario qu'on a étudié avec Awa et Paul.
        toAccount.credit(amount);

        // Pas besoin d'appeler explicitement accountRepository.save() :
        // les entités sont "managées" par JPA dans le contexte de la
        // transaction, et leurs modifications sont automatiquement
        // synchronisées en base au moment du COMMIT.
    }


    @Transactional
    public void withdraw(String reference, BigDecimal amount) {
        Account account = accountRepository.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Compte introuvable : " + reference
                ));

        account.debit(amount); // lève InsufficientBalanceException si besoin

        // Pas de save() explicite nécessaire : JPA synchronise
        // automatiquement au COMMIT.
    }
}
