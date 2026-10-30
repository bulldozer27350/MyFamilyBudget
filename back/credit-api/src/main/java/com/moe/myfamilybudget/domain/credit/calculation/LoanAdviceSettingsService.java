package com.moe.myfamilybudget.domain.credit.calculation;

/**
 * Hypothèses de l'analyse des prêts modifiables depuis l'application. Sans enregistrement, les
 * valeurs par défaut de {@link LoanAdviceParameters#defaults(java.math.BigDecimal)} s'appliquent.
 *
 * <p>L'implémentation vit dans {@code credit-core} ({@code DefaultLoanAdviceSettingsService}).
 */
public interface LoanAdviceSettingsService {

    /** Hypothèses en vigueur : enregistrées si elles existent, sinon celles par défaut. */
    LoanAdviceParameters current();

    /** Valeurs par défaut (sans taux de marché). */
    LoanAdviceParameters defaults();

    /**
     * Valide puis enregistre les hypothèses.
     *
     * @throws IllegalArgumentException si une valeur est absente ou hors plage (traduit en 400)
     */
    LoanAdviceParameters save(LoanAdviceParameters parameters);
}
