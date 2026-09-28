package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne de charge normalisée pour la projection de trésorerie (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md).
 *
 * <p>{@code growthRate} reste {@code null} tel quel (pas résolu au niveau du contrat) : c'est
 * {@code TreasuryParameters#inflationRate} qui sert de valeur par défaut lors du calcul, comme le
 * fait aujourd'hui {@code chargeEffectiveGrowth}.
 *
 * @param label      libellé de la charge
 * @param monthly    montant mensuel de départ ({@code null} vaut 0)
 * @param start      premier mois actif, {@code null} si non renseigné (ligne alors sans effet)
 * @param end        dernier mois actif, {@code null} si non renseigné (ligne alors sans effet)
 * @param growthRate taux de croissance annuel saisi, {@code null} pour utiliser l'inflation par défaut
 */
public record ChargeProjectionInput(String label, BigDecimal monthly, LocalDate start, LocalDate end, BigDecimal growthRate) {
    public ChargeProjectionInput {
        monthly = monthly != null ? monthly : BigDecimal.ZERO;
    }
}
