package com.moe.myfamilybudget.domain.treasury.calculation;

import java.math.BigDecimal;

/**
 * Interface de service du silo Trésorerie (SILO-153, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur de trésorerie pour les consommateurs ({@code application}, tests) : ils ne connaissent
 * ni son implémentation ({@code DefaultTresorerieCalculationService}, dans {@code treasury-core}), ni son câblage
 * (déclaré par le composition root). Reçoit exclusivement {@link TreasuryProjectionInput} et les modèles minimaux
 * {@link ChargeProjectionInput} et {@link IncomeProjectionInput} : aucun {@code BudgetDataModel}, aucun autre
 * modèle persistant.
 */
public interface TresorerieCalculationService {

    /** Projette les flux de trésorerie année par année, avec l'aperçu des revenus variables. */
    TreasuryProjection compute(TreasuryProjectionInput input);

    /** Montant mensuel d'une charge pour une année donnée (zéro hors période ou pour une entrée {@code null}). */
    BigDecimal chargeMonthlyForYear(ChargeProjectionInput charge, int year, BigDecimal inflationRate);

    /** Montant annuel d'une charge pour une année donnée (mois actifs de l'année, zéro hors période). */
    BigDecimal chargeAnnualForYear(ChargeProjectionInput charge, int year, BigDecimal inflationRate);

    /** Montant mensuel d'un revenu pour une année donnée (zéro hors période ou pour une entrée {@code null}). */
    BigDecimal incomeMonthlyForYear(IncomeProjectionInput income, int year);

    /** Montant annuel d'un revenu pour une année donnée (mois actifs de l'année, zéro hors période). */
    BigDecimal incomeAnnualForYear(IncomeProjectionInput income, int year);
}
