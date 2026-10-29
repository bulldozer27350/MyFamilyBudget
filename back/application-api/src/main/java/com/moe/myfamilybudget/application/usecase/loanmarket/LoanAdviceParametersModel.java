package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Hypothèses de l'analyse des prêts lisibles/modifiables (valeurs actuelles + valeurs par défaut)
 * exposées par l'application (R-86, SILO-317).
 *
 * <p>Miroir applicatif du résultat de {@code getAnalysePretsParametres} et
 * {@code updateAnalysePretsParametres}.
 *
 * @param values   hypothèses actuellement enregistrées
 * @param defaults hypothèses par défaut (sans taux de marché)
 */
public record LoanAdviceParametersModel(
        LoanAdviceParametersValuesModel values,
        LoanAdviceParametersValuesModel defaults) {
}
