package com.moe.myfamilybudget.domain.settings.model;

/**
 * Evenement publie par l'adaptateur du silo Parametres apres chaque ecriture de {@code simulateUntilAge} ou de
 * {@code inflationRate} (R-50, DA-08).
 *
 * <p>Depuis que ces parametres s'ecrivent directement dans {@code app_settings}, ils ne passent plus par le
 * {@code PersistenceManager} et son {@code BudgetMutatedEvent} : cet evenement conserve la regle « toute
 * modification declenche un controle des notifications ». Il est ecoute apres le commit de la transaction en
 * cours, dans le composition root.
 *
 * @param mutationKind nature de l'ecriture (diagnostic uniquement : {@code updateSimulateUntilAge},
 *                     {@code updateInflationRate}, {@code replaceSimulation}, {@code resetSimulation},
 *                     {@code replaceEconomicAssumptions}, {@code resetEconomicAssumptions})
 */
public record AppSettingsMutatedEvent(String mutationKind) {
}
