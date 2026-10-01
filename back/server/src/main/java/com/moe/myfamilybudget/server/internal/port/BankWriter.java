package com.moe.myfamilybudget.server.internal.port;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;

/**
 * Port d'ecriture pour le domaine Banque / Import (DB-040). Seul le command service du domaine
 * ({@code BankImportCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et
 * prepare la bascule JPA (DB-1031) sans changer l'appelant.
 *
 * <p>Le format JSON interne de {@code BankImportEntity} reste une decision de la persistance Banque :
 * le port ne manipule que le modele {@link BankImportModel}.
 */
public interface BankWriter {

    /** Remplace l'import bancaire (transactions, categories, regles, pointages, operations en cours). */
    void updateBankImport(BankImportModel bankImport);
}
