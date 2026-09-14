package com.djokodev.payment_core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByReference(String reference);

    /**
     * Version verrouillée de la lecture d'un compte, équivalente à un
     * SELECT ... FOR UPDATE en SQL brut.
     *
     * LockModeType.PESSIMISTIC_WRITE dit à Hibernate : "verrouille cette
     * ligne dès la lecture, et empêche toute autre transaction de la lire
     * avec le même mode de verrouillage tant que je n'ai pas fait
     * COMMIT ou ROLLBACK."
     *
     * On dit "pessimiste" car on part du principe qu'un conflit va
     * probablement arriver, donc on bloque préventivement plutôt que
     * de laisser la course se produire et de la détecter après coup.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT a FROM Account a WHERE a.reference = :reference")
    Optional<Account> findByReferenceForUpdate(String reference);
}
