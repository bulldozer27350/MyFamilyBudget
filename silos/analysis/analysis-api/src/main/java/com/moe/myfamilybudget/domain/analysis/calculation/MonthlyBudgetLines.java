package com.moe.myfamilybudget.domain.analysis.calculation;

import java.util.Collections;
import java.util.List;

/**
 * Lignes budgétaires actives d'un mois donné (RF-600, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>Une entrée par mois de la fenêtre d'{@link AnalyseInput#monthlyBudgetLines()}, y compris le
 * mois courant (celui de {@link AnalysisPeriod#today()}) : il n'y a pas de champ séparé pour les
 * lignes du mois courant. Composée en amont par {@code AnalyseInputFactory} via
 * {@code PointageInputFactory.activeBudgetLines} (RF-501), traduites vers {@link AnalysisBudgetLine} (SILO-133), un mois à la fois.
 *
 * @param monthISO mois au format {@code YYYY-MM}
 * @param lines    lignes budgétaires actives pour ce mois
 */
public record MonthlyBudgetLines(String monthISO, List<AnalysisBudgetLine> lines) {

    public MonthlyBudgetLines {
        if (monthISO == null) monthISO = "";
        if (lines == null) lines = Collections.emptyList();
    }
}
