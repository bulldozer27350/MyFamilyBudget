package com.moe.myfamilybudget.domain.analysis.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ligne budgétaire active pour un mois, telle que le silo Analyse la lit (SILO-133) : aucun type du
 * silo Banque/Pointage ni du silo Budget. Composée et traduite en amont par l'application.
 *
 * @param id      identifiant de la ligne source, référencé par les rapprochements
 * @param label   libellé affichable
 * @param kind    nature de la ligne : {@code "charge"} (défaut), {@code "revenu"} ou {@code "placement"}
 * @param monthly montant prévu pour le mois (2 décimales)
 */
public record AnalysisBudgetLine(String id, String label, String kind, BigDecimal monthly) {

    public AnalysisBudgetLine {
        if (id == null) id = "";
        if (label == null) label = "";
        if (kind == null) kind = "charge";
        monthly = monthly == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : monthly.setScale(2, RoundingMode.HALF_UP);
    }
}
