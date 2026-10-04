package com.moe.myfamilybudget.transition.model;

/**
 * Paramètre de la notion Simulation (SILO-100) : profondeur de simulation. Aucun silo n'existe pour cette
 * notion ; le modèle vit à côté de {@code SimulationSettingsWriter}, dans le module de transition.
 */
public record SimulationSettingsModel(Integer simulateUntilAge) {

    public int getEffectiveSimulateUntilAge() {
        return simulateUntilAge != null ? simulateUntilAge : 85;
    }
}
