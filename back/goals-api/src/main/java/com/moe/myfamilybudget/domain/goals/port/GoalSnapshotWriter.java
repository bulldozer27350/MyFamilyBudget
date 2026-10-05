package com.moe.myfamilybudget.domain.goals.port;

import java.util.List;

import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;

/**
 * Port d'import et de réinitialisation des objectifs du silo Objectifs (SILO-119, lot B1). L'export passe par
 * {@link GoalReader}. Les paramètres du domaine Objectifs restent portés par leur propre service (RF-700).
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que les objectifs.
 */
public interface GoalSnapshotWriter {

    /** Remplace les objectifs ; une liste {@code null} est lue comme vide. */
    void replace(List<ObjectifModel> goals);

    /** Supprime tous les objectifs. */
    void reset();
}
