package com.moe.myfamilybudget.domain.tax.calculation;

import java.math.BigDecimal;

/**
 * Paramètres du foyer nécessaires au calcul fiscal (voir {@link TaxCalculationInput}).
 *
 * @param birthYear      année de naissance du contribuable principal
 * @param retireAge      âge de départ à la retraite retenu (détermine l'année de retraite)
 * @param childExitAge   âge auquel un enfant cesse d'être rattaché au foyer fiscal
 * @param taxAbattement  abattement (fraction) appliqué au revenu brut avant calcul de l'impôt
 */
public record TaxHouseholdParameters(int birthYear, int retireAge, int childExitAge, BigDecimal taxAbattement) {
}
