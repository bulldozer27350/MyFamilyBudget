package com.moe.myfamilybudget.domain.wealth.calculation;

import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;

/**
 * Interface de service du silo Patrimoine, projection patrimoniale annuelle (SILO-152, voir
 * doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur de projection pour les consommateurs ({@code application}, tests) : ils ne
 * connaissent ni son implémentation ({@code DefaultPatrimoineProjectionService}, dans {@code wealth-core}),
 * ni son câblage (déclaré par le composition root). Reçoit exclusivement {@link PatrimoineProjectionInput} :
 * aucun {@code BudgetDataModel}, aucun autre modèle persistant.
 */
public interface PatrimoineProjectionService {

    /**
     * Projette chaque placement année par année dans les trois scénarios, puis agrège le total
     * (éventuellement en euros constants).
     */
    PatrimoineProjectionsModel compute(PatrimoineProjectionInput input, boolean useConstantEuros);
}
