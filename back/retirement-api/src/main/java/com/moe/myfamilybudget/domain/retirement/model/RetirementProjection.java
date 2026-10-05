package com.moe.myfamilybudget.domain.retirement.model;

import java.util.List;

/**
 * Résultat du moteur de calcul retraite centralisé (RF-101, voir
 * doc/architecture/03-domaine-retraite.md) : une projection par personne, dans le même ordre
 * que les {@code RetirementPersonInput} fournis en entrée à
 * {@code RetirementCalculationService.compute(...)}.
 */
public record RetirementProjection(List<RetirementProjectionModel> people) {
    public RetirementProjection {
        people = people != null ? List.copyOf(people) : List.of();
    }
}
