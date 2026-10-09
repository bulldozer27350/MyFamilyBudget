package com.moe.myfamilybudget.application.command;

import com.moe.myfamilybudget.application.port.MutationSilo;

/**
 * Owner applicatif d'un paramètre de {@code /settings} (SET-010 / SET-020, voir
 * doc/architecture/12-settings.md). La façade REST reste unique ; chaque propriété est écrite par son owner.
 * SILO-206 : chaque owner désigne le silo à verrouiller pour l'écrire.
 */
public enum SettingsOwner {
    RETRAITE(MutationSilo.RETIREMENT),
    FISCALITE(MutationSilo.TAX),
    TRESORERIE(MutationSilo.TREASURY),
    OBJECTIFS(MutationSilo.GOALS),
    SIMULATION(MutationSilo.SETTINGS),
    HYPOTHESES_ECONOMIQUES(MutationSilo.SETTINGS);

    private final MutationSilo silo;

    SettingsOwner(MutationSilo silo) {
        this.silo = silo;
    }

    /** Silo à verrouiller pour écrire un paramètre de cet owner. */
    public MutationSilo silo() {
        return silo;
    }
}
