package com.moe.myfamilybudget.server.internal.calculation;

import java.util.Optional;

/**
 * Persistance des paramètres du domaine Objectifs (RF-700).
 */
public interface ObjectifsSettingsStore {

    /** Paramètres enregistrés, ou vide s'il n'y en a pas. */
    Optional<ObjectifsParameters> load();

    void save(ObjectifsParameters parameters);

    /** Supprime les paramètres enregistrés (retour aux valeurs par défaut). */
    void clear();
}
