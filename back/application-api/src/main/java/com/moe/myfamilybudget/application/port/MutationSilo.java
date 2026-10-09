package com.moe.myfamilybudget.application.port;

/**
 * Silo dont une façade transactionnelle verrouille l'écriture (SILO-206, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>L'ordre de déclaration est l'ordre de prise des verrous : une transaction qui verrouille plusieurs silos les
 * prend toujours du plus petit au plus grand, ce qui écarte l'interblocage entre deux façades. Ne pas réordonner
 * ces valeurs sans revoir {@link SiloMutationLock}.
 *
 * <p>{@link #SETTINGS} est le silo Paramètres (DA-06) : {@code simulateUntilAge} et {@code inflationRate}, écrits
 * directement dans la table {@code app_settings} (R-50). Les deux paramètres partagent une même ligne : un seul
 * verrou les protège, pour qu'une écriture de l'un ne perde pas une écriture concurrente de l'autre.
 */
public enum MutationSilo {
    RETIREMENT,
    TAX,
    TREASURY,
    WEALTH,
    CREDIT,
    GOALS,
    BANK_POINTAGE,
    SETTINGS
}
