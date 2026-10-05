package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un individu de la section Retraite avec sa projection calculée.
 */
public record RetraitePersonWithProjectionModel(
    String id,
    String name,
    Integer birthYear,
    String incomeLabel,
    Integer trimestresValides,
    String trimestresDate,
    List<RetraiteSalaryHistoryModel> salaryHistory,
    BigDecimal agircPoints,
    BigDecimal ratioPointsParEuro,
    Boolean cadre,
    RetraiteProjectionModel projection
) {}
