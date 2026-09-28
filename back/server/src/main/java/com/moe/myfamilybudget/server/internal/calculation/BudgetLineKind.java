package com.moe.myfamilybudget.server.internal.calculation;

/**
 * Nature (charge, revenu ou placement) d'une ligne budgétaire, y compris hors de sa période active
 * (RF-600, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>{@link MonthlyBudgetLines} ne porte que les lignes actives d'un mois : une charge terminée
 * avant la fenêtre d'analyse n'y apparaît plus, mais son historique de transactions bancaires peut
 * encore être rattaché à sa nature. {@code AnalyseInputFactory} doit produire une entrée par
 * identifiant de charge, revenu et placement connu, dans cet ordre (une ligne existant dans
 * plusieurs listes garde la nature de la première correspondance — charge, puis revenu, puis
 * placement — comme dans l'ancien calcul par écrasement successif).
 *
 * @param lineId identifiant de la charge, du revenu ou du placement
 * @param kind   {@code "charge"}, {@code "revenu"} ou {@code "placement"}
 */
public record BudgetLineKind(String lineId, String kind) {

    public BudgetLineKind {
        if (lineId == null) lineId = "";
        if (kind == null) kind = "";
    }
}
