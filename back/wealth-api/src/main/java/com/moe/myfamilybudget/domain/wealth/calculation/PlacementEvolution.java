package com.moe.myfamilybudget.domain.wealth.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Résultat de {@link PlacementEvolutionService} : chronologie d'un placement, sans aucun élément de
 * présentation (horodatages et libellés sont produits par le mapper).
 *
 * @param placementId identifiant du placement tracé
 * @param anchorDate  date du dernier point réel connu, d'où repartent les trois projections
 * @param today       date du jour
 * @param points      points par ordre chronologique
 */
public record PlacementEvolution(
        String placementId, LocalDate anchorDate, LocalDate today, List<Point> points) {

    public PlacementEvolution {
        points = points != null ? List.copyOf(points) : List.of();
    }

    /**
     * Un point de la chronologie : la valeur réelle et/ou les trois projections ({@code null} là
     * où la valeur n'existe pas à cette date).
     */
    public record Point(LocalDate date, BigDecimal real, BigDecimal pess, BigDecimal corr, BigDecimal opti) {
    }
}
