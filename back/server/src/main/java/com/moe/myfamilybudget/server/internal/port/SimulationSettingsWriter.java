package com.moe.myfamilybudget.server.internal.port;

/**
 * Port d'écriture de la notion Simulation (SET-020) : profondeur de simulation. Seul
 * {@code SimulationSettingsCommandService} l'utilise. Le stockage physique reste partagé avec les
 * autres paramètres ; sa séparation relève des patchs {@code DB-xxx}.
 */
public interface SimulationSettingsWriter {

    /** Met à jour {@code simulateUntilAge} ; une valeur illisible retombe sur la valeur par défaut historique. */
    void updateSimulateUntilAge(Object value);
}
