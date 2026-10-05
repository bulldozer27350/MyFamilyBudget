package com.moe.myfamilybudget.domain.retirement.calculation;

import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;

/**
 * Interface de service du silo Retraite (SILO-150, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Seule vue du moteur de calcul retraite pour les consommateurs ({@code application}, tests) : ils ne
 * connaissent ni son implémentation ({@code DefaultRetirementCalculationService}, dans {@code retirement-core}),
 * ni son câblage (déclaré par le composition root). Reçoit exclusivement {@link RetirementCalculationInput} :
 * aucun {@code BudgetDataModel}, aucun autre modèle persistant.
 */
public interface RetirementCalculationService {

    /** Calcule la projection de retraite de chaque personne de l'entrée, dans l'ordre de celle-ci. */
    RetirementProjection compute(RetirementCalculationInput input);
}
