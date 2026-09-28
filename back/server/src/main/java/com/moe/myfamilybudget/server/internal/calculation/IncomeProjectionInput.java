package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne de revenu régulier normalisée pour la projection de trésorerie (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md), y compris les pensions de retraite déjà
 * annualisées côté {@link RetirementIncomeProjection} (traitées séparément, pas ici).
 *
 * <p>Projection minimale de {@code IncomeModel} : ni {@code id}, ni {@code categoryId}, ni
 * {@code notes}, qui n'intéressent pas le calcul.
 *
 * @param label       libellé de la ligne (sert au rapprochement des revenus variables)
 * @param monthly     montant mensuel de départ ({@code null} vaut 0)
 * @param start       premier mois actif, {@code null} si non renseigné (ligne alors sans effet)
 * @param end         dernier mois actif, {@code null} si non renseigné (ligne alors sans effet)
 * @param growthRate  taux de croissance annuel ({@code null} vaut 0)
 */
public record IncomeProjectionInput(String label, BigDecimal monthly, LocalDate start, LocalDate end, BigDecimal growthRate) {
    public IncomeProjectionInput {
        monthly = monthly != null ? monthly : BigDecimal.ZERO;
        growthRate = growthRate != null ? growthRate : BigDecimal.ZERO;
    }
}
