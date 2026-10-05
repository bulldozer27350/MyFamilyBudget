package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;
import java.util.List;

/**
 * Modèle applicatif de la réponse Retraite : données de retraite avec projections, année de départ,
 * revenus et paramètres généraux.
 */
public record RetraiteResultModel(
    RetirementWithProjectionsModel retirement,
    Integer retireYear,
    List<RetraiteIncomeModel> incomes,
    RetraiteSettingsModel settings
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
