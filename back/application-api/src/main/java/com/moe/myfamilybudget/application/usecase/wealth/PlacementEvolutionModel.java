package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Chronologie d'un placement (segment réel suivi des trois projections), résultat de
 * {@link WealthUseCase#getPlacementEvolution}.
 */
public record PlacementEvolutionModel(
    String placementId,
    LocalDate anchorDate,
    LocalDate today,
    List<Point> points
) {

    /** Un point de la chronologie : la valeur réelle et/ou les trois projections. */
    public record Point(LocalDate date, BigDecimal real, BigDecimal pess, BigDecimal corr, BigDecimal opti) {}
}
