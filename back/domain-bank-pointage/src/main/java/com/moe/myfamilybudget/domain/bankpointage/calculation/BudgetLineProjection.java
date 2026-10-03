package com.moe.myfamilybudget.domain.bankpointage.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ligne budgétaire active pour un mois, sous une forme neutre (RF-500, voir
 * doc/architecture/07-domaine-banque-pointage.md et 08-domaine-analyse.md).
 *
 * <p>Type partagé par Pointage (RF-501) et Analyse (RF-600) : un consommateur ne doit pas savoir
 * si la ligne provient d'un {@code ChargeModel}, d'un {@code IncomeModel} ou d'un
 * {@code PlacementModel}. Sa construction est une étape de composition en amont, hors des moteurs
 * de calcul.
 *
 * @param id         identifiant de la ligne source, référencé par les rapprochements bancaires
 * @param label      libellé affichable
 * @param kind       nature de la ligne : {@code "charge"}, {@code "revenu"} ou {@code "placement"}
 * @param monthly    montant prévu pour le mois (2 décimales)
 * @param categoryId catégorie associée, chaîne vide si aucune
 */
public record BudgetLineProjection(String id, String label, String kind, BigDecimal monthly, String categoryId) {

    public BudgetLineProjection {
        if (id == null) id = "";
        if (label == null) label = "";
        if (kind == null) kind = "charge";
        monthly = monthly == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : monthly.setScale(2, RoundingMode.HALF_UP);
        if (categoryId == null) categoryId = "";
    }
}
