package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Représente un individu dans la section Retraite avec sa projection calculée.
 */
public record RetraitePersonWithProjectionModel(
    String id,
    String name,
    Integer birthYear,
    String incomeLabel,
    Integer trimestresValides,
    String trimestresDate,
    List<RetirementModel.SalaryHistoryModel> salaryHistory,
    BigDecimal agircPoints,
    BigDecimal ratioPointsParEuro,
    RetirementProjectionModel projection
) {}
