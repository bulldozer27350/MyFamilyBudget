package com.moe.myfamilybudget.domain.credit.port;

import java.util.List;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;

/**
 * Port d'import et de réinitialisation du silo Crédit (SILO-119, lot B1). L'export passe par
 * {@link LoanReader}.
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que les prêts.
 */
public interface LoanSnapshotWriter {

    /** Remplace les prêts ; une liste {@code null} est lue comme vide. */
    void replace(List<LoanModel> loans);

    /** Supprime tous les prêts. */
    void reset();
}
