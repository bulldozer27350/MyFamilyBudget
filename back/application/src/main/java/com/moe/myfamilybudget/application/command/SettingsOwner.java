package com.moe.myfamilybudget.application.command;

/**
 * Owner applicatif d'un paramètre de {@code /settings} (SET-010 / SET-020, voir
 * doc/architecture/12-settings.md). La façade REST reste unique ; chaque propriété est écrite par son owner.
 */
public enum SettingsOwner {
    RETRAITE,
    FISCALITE,
    TRESORERIE,
    OBJECTIFS,
    SIMULATION,
    HYPOTHESES_ECONOMIQUES
}
