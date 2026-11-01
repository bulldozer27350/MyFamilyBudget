package com.moe.myfamilybudget.domain.goals.port;

import java.util.Map;

/**
 * Port d'ecriture pour le domaine Objectifs (DB-041). Seul le command service du domaine
 * ({@code GoalCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et du
 * command Patrimoine, et prepare la bascule JPA (DB-1021) sans changer l'appelant.
 *
 * <p>Le corps generique est conserve tel quel : son retrait releve de DB-050.
 */
public interface GoalWriter {

    /** Cree ou remplace un objectif ; renvoie la ligne enregistree (dont son {@code id}). */
    Map<String, Object> saveGoalRow(Map<String, Object> body);

    void deleteGoalRow(String id);
}
