package com.moe.myfamilybudget.domain.settings.port;

import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;

/**
 * Port d'import et de réinitialisation des hypothèses économiques (SILO-119, lot B1). Lecture :
 * {@link EconomicAssumptionsReader}. Port de transition, supprimé avec {@code transition-snapshot} (SILO-230).
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation.
 */
public interface EconomicAssumptionsSnapshotWriter {

    /** Remplace les hypothèses économiques ({@code null} accepté : valeur par défaut). */
    void replace(EconomicAssumptionsModel assumptions);

    /** Remet les hypothèses économiques à leurs valeurs par défaut. */
    void reset();
}
