package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paramètres de la chronologie d'un placement (RF-301, voir doc/architecture/05-domaine-patrimoine.md).
 *
 * @param today              date du jour, ancrage de repli quand ni historique ni date de solde n'existent
 * @param horizonYears       nombre d'années projetées à partir du point d'ancrage
 * @param inflationRate      taux d'inflation annuel, pour la conversion en euros constants ({@code null} vaut 0)
 * @param startBalance       solde de trésorerie de départ ({@code null} vaut 0)
 * @param cashCeiling        plafond de trésorerie relâchant la pause, {@code null} si non configuré
 * @param cashAlertThreshold seuil d'alerte de trésorerie déclenchant la pause, {@code null} si non configuré
 */
public record PlacementEvolutionParameters(
        LocalDate today,
        int horizonYears,
        BigDecimal inflationRate,
        BigDecimal startBalance,
        BigDecimal cashCeiling,
        BigDecimal cashAlertThreshold) {

    public PlacementEvolutionParameters {
        inflationRate = inflationRate != null ? inflationRate : BigDecimal.ZERO;
        startBalance = startBalance != null ? startBalance : BigDecimal.ZERO;
    }
}
