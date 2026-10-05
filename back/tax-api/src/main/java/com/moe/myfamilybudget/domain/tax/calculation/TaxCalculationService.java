package com.moe.myfamilybudget.domain.tax.calculation;

import java.util.List;

import com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel;

/**
 * Interface de service du silo Fiscalité (SILO-151, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur fiscal pour les consommateurs ({@code application}, tests) : ils ne connaissent ni son
 * implémentation ({@code DefaultTaxCalculationService}, dans {@code tax-core}), ni son câblage (déclaré par le
 * composition root). Reçoit exclusivement {@link TaxCalculationInput} : aucun modèle persistant.
 */
public interface TaxCalculationService {

    /** Calcule les projections fiscales annuelles ; une entrée {@code null} ou incomplète donne une liste vide. */
    List<TaxYearlyModel> computeTaxYearly(TaxCalculationInput input);

    /** Sélectionne la fenêtre de prévisualisation (jusqu'à 6 ans à partir de l'année courante, ou les 6 dernières). */
    List<TaxYearlyModel> buildTaxPreview(List<TaxYearlyModel> taxYearly, int currentYear);
}
