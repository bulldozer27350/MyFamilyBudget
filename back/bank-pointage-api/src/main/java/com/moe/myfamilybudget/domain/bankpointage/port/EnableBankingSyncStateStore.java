package com.moe.myfamilybudget.domain.bankpointage.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Port de persistance de l'état de synchronisation Enable Banking : date de la dernière opération
 * comptabilisée importée, par compte. Implémenté hors du silo (adaptateur JPA du composition root, déplacé
 * dans la persistance du silo Banque par SILO-213).
 */
public interface EnableBankingSyncStateStore {

    /** Date (ISO {@code yyyy-MM-dd}) de la dernière opération comptabilisée importée pour ce compte, si connue. */
    Optional<String> findLastBookingDate(String accountUid);

    /** Enregistre (ou remplace) l'état de synchronisation du compte. */
    void save(String accountUid, String lastBookingDate, Instant lastSyncAt);
}
