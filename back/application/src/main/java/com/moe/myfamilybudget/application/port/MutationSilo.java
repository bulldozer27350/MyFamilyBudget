package com.moe.myfamilybudget.application.port;

/**
 * Silo dont une façade transactionnelle verrouille l'écriture (SILO-206, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>L'ordre de déclaration est l'ordre de prise des verrous : une transaction qui verrouille plusieurs silos les
 * prend toujours du plus petit au plus grand, ce qui écarte l'interblocage entre deux façades. Ne pas réordonner
 * ces valeurs sans revoir {@link SiloMutationLock}.
 *
 * <p>{@link #SIMULATION} et {@link #ECONOMIC_ASSUMPTIONS} sont des paramètres transverses encore stockés dans
 * {@code SettingsEntity} ; ils rejoindront leurs silos propriétaires avec SILO-220.
 */
public enum MutationSilo {
    RETIREMENT,
    TAX,
    TREASURY,
    WEALTH,
    CREDIT,
    GOALS,
    BANK_POINTAGE,
    SIMULATION,
    ECONOMIC_ASSUMPTIONS
}
