package com.moe.myfamilybudget.domain.retirement.model;

/**
 * Paramètres généraux dont Retraite est propriétaire (SILO-100) : année de naissance et âge de départ.
 * {@code pass2026} et {@code passGrowthRate} restent portés par {@link RetirementModel}.
 *
 * <p>Les valeurs par défaut sont celles historiquement appliquées par {@code SettingsModel}.
 */
public record RetirementSettingsModel(Integer birthYear, Integer retireAge) {

    public int getEffectiveBirthYear() {
        return birthYear != null ? birthYear : 1985;
    }

    public int getEffectiveRetireAge() {
        return retireAge != null ? retireAge : 64;
    }
}
