package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Modèle de domaine représentant les données de retraite complètes avec projections.
 */
public record RetraiteResultModel(
    RetirementWithProjectionsModel retirement,
    Integer retireYear,
    List<IncomeModel> incomes,
    SettingsModel settings
) {
    public record RetirementWithProjectionsModel(
        List<RetraitePersonWithProjectionModel> people,
        BigDecimal pass2026,
        BigDecimal passGrowthRate,
        BigDecimal agircPointValue,
        String agircPointDateGlobal,
        BigDecimal agircPointGrowthRate
    ) {}
}
