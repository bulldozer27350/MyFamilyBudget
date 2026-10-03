package com.moe.myfamilybudget.application.model;

import java.math.BigDecimal;
import java.util.List;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;

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
    Boolean cadre,
    RetirementProjectionModel projection
) {}
