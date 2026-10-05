package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;
import java.util.List;

/** Une personne de la commande de sauvegarde Retraite. */
public record RetraitePersonCommand(
    String id,
    String name,
    Integer birthYear,
    String incomeLabel,
    Integer trimestresValides,
    String trimestresDate,
    List<RetraiteSalaryHistoryModel> salaryHistory,
    BigDecimal agircPoints,
    BigDecimal ratioPointsParEuro,
    Boolean cadre
) {}
