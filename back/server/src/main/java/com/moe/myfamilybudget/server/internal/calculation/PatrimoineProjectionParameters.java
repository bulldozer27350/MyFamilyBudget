package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Paramètres de la projection patrimoniale (RF-300, voir doc/architecture/05-domaine-patrimoine.md
 * et 12-settings.md). Les seuils de trésorerie sont détenus par le domaine Trésorerie et n'entrent
 * ici que comme valeurs consommées par la pause automatique des versements.
 *
 * @param startYear          première année projetée (incluse)
 * @param endYear            dernière année projetée (incluse)
 * @param inflationRate      taux d'inflation annuel, pour la conversion en euros constants
 *                           ({@code null} vaut 0)
 * @param startBalance       solde de trésorerie de départ ({@code null} vaut 0)
 * @param cashCeiling        plafond de trésorerie relâchant la pause, {@code null} si non configuré
 * @param cashAlertThreshold seuil d'alerte de trésorerie déclenchant la pause, {@code null} si non configuré
 */
public record PatrimoineProjectionParameters(
        int startYear,
        int endYear,
        BigDecimal inflationRate,
        BigDecimal startBalance,
        BigDecimal cashCeiling,
        BigDecimal cashAlertThreshold) {

    public PatrimoineProjectionParameters {
        inflationRate = inflationRate != null ? inflationRate : BigDecimal.ZERO;
        startBalance = startBalance != null ? startBalance : BigDecimal.ZERO;
    }
}
