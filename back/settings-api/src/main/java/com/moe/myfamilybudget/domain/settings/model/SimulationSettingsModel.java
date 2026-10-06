package com.moe.myfamilybudget.domain.settings.model;

/**
 * Paramètre de la notion Simulation (SILO-100) : profondeur de simulation. Propriété de l'application, sans
 * propriétaire métier : elle appartient au silo Paramètres (SILO-220).
 */
public record SimulationSettingsModel(Integer simulateUntilAge) {

    public int getEffectiveSimulateUntilAge() {
        return simulateUntilAge != null ? simulateUntilAge : 85;
    }
}
