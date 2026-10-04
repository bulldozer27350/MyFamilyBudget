package com.moe.myfamilybudget.domain.retirement.port;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;

/**
 * Port d'import et de réinitialisation du silo Retraite (SILO-119, lot B1) : l'application décompose le JSON
 * global en fragments et confie chacun à son propriétaire. L'export passe par {@link RetirementReader} et
 * {@link RetirementSettingsReader}.
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que les données du silo Retraite.
 */
public interface RetirementSnapshotWriter {

    /** Remplace les paramètres Retraite et le plan de retraite ({@code null} accepté : absent). */
    void replace(RetirementSettingsModel settings, RetirementModel retirement);

    /** Remet le silo Retraite à ses valeurs par défaut. */
    void reset();
}
