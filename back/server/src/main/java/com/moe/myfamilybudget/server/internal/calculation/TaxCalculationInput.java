package com.moe.myfamilybudget.server.internal.calculation;

import java.util.List;

/**
 * Contrat d'entrée du domaine Fiscalité (RF-200, voir doc/architecture/04-domaine-fiscalite.md).
 *
 * <p>Version 1 (« large ») : {@code retirementIncome} est encore construit par
 * {@code TaxInputFactory} à partir du modèle persistant (via la logique historique de pension de
 * {@code TaxCalculator}), pas encore depuis {@code RetirementCalculationService} — cette
 * substitution est l'objet de RF-202, qui dépend de RF-102 (Retraite branchée sur le moteur
 * unique). {@code period} est de même encore dérivé de {@code findEarliestYear(BudgetDataModel)}
 * par la Factory ; RF-202 le fera calculer en amont, côté application.
 *
 * @param period            horizon de simulation (années incluses, bornes comprises)
 * @param household         paramètres du foyer nécessaires au calcul (parts, décote, abattement)
 * @param incomes           revenus réguliers déjà projetés, un montant annuel par année de {@code period}
 * @param variableIncomes   part imposable des revenus variables, un montant annuel par année de {@code period}
 * @param childBirthYears   années de naissance des enfants pris en compte pour le quotient familial
 * @param brackets          barème progressif de l'impôt sur le revenu
 * @param rateOverrides     taux de prélèvement à la source saisis manuellement, par année
 * @param actualOverrides   montants d'impôt réel saisis manuellement (avis d'imposition), par année
 * @param retirementIncome  pension imposable déjà projetée, un montant annuel par année de {@code period}
 */
public record TaxCalculationInput(
        TaxSimulationPeriod period,
        TaxHouseholdParameters household,
        List<AnnualTaxIncome> incomes,
        List<AnnualVariableIncome> variableIncomes,
        List<Integer> childBirthYears,
        List<TaxBracket> brackets,
        List<TaxRateOverride> rateOverrides,
        List<TaxActualOverride> actualOverrides,
        List<AnnualTaxableRetirementIncome> retirementIncome) {
}
