package com.moe.myfamilybudget.domain.bankpointage.port;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;

/**
 * Port d'import et de réinitialisation du silo Banque/Pointage (SILO-119, lot B1). L'export passe par
 * {@link BankReader}.
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que l'import bancaire.
 */
public interface BankSnapshotWriter {

    /** Remplace l'import bancaire ({@code null} accepté : absent). */
    void replace(BankImportModel bankImport);

    /** Remet l'import bancaire à vide (aucune transaction, aucun relevé). */
    void reset();
}
