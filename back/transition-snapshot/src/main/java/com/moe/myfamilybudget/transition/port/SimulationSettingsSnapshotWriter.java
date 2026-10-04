package com.moe.myfamilybudget.transition.port;

import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;

/**
 * Port d'import et de réinitialisation des paramètres de simulation (SILO-119, lot B1). Lecture :
 * {@link SimulationSettingsReader}. Port de transition, supprimé avec {@code transition-snapshot} (SILO-230).
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation.
 */
public interface SimulationSettingsSnapshotWriter {

    /** Remplace les paramètres de simulation ({@code null} accepté : valeur par défaut). */
    void replace(SimulationSettingsModel settings);

    /** Remet les paramètres de simulation à leurs valeurs par défaut. */
    void reset();
}
