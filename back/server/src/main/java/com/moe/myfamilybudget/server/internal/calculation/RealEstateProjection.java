package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Projection du patrimoine immobilier à l'horizon de retraite (RF-900, voir
 * doc/architecture/11-domaine-overview.md).
 *
 * @param nominalValueAtRetire valeur estimée totale des biens à l'année de départ à la retraite
 *                             (valeur nominale projetée, hors déflateur d'euros constants)
 * @param currentTotalValue    valeur totale actuelle estimée des biens
 * @param items                détail des biens immobiliers projetés
 */
public record RealEstateProjection(
        BigDecimal nominalValueAtRetire,
        BigDecimal currentTotalValue,
        List<RealEstateItemProjection> items) {

    public RealEstateProjection {
        nominalValueAtRetire = nominalValueAtRetire != null ? nominalValueAtRetire : BigDecimal.ZERO;
        currentTotalValue = currentTotalValue != null ? currentTotalValue : BigDecimal.ZERO;
        items = items != null ? List.copyOf(items) : List.of();
    }

    public RealEstateProjection(BigDecimal nominalValueAtRetire) {
        this(nominalValueAtRetire, BigDecimal.ZERO, List.of());
    }

    /**
     * Projection unitaire d'un bien immobilier.
     *
     * @param label                libellé du bien
     * @param currentValue         valeur actuelle déclarée
     * @param valuationYear        année d'estimation de la valeur actuelle
     * @param annualGrowthRate     taux annuel de croissance estimé
     * @param nominalValueAtRetire valeur projetée à la date de départ à la retraite
     */
    public record RealEstateItemProjection(
            String label,
            BigDecimal currentValue,
            int valuationYear,
            BigDecimal annualGrowthRate,
            BigDecimal nominalValueAtRetire) {

        public RealEstateItemProjection {
            currentValue = currentValue != null ? currentValue : BigDecimal.ZERO;
            annualGrowthRate = annualGrowthRate != null ? annualGrowthRate : BigDecimal.ZERO;
            nominalValueAtRetire = nominalValueAtRetire != null ? nominalValueAtRetire : BigDecimal.ZERO;
        }
    }
}
