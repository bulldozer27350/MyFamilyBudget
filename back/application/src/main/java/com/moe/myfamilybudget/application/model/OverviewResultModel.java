package com.moe.myfamilybudget.application.model;

import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;
import java.math.BigDecimal;
import java.util.List;
import com.moe.myfamilybudget.domain.treasury.model.CashflowYearModel;
import com.moe.myfamilybudget.domain.treasury.model.TripleAmountModel;

/**
 * Résultat du calcul de l'aperçu financier global (Overview).
 *
 * <p>RF-901 : suppression du champ {@code BudgetDataModel data} (fuite de résultat).
 */
public record OverviewResultModel(
    List<Integer> years,
    List<CashflowYearModel> cashflow,
    PatrimoineProjectionsModel patrimoine,
    boolean useConstantEuros,
    int retireYear,
    BigDecimal pivotBalance,
    BigDecimal patrimoineActuel,
    BigDecimal fluxNetActuel,
    BigDecimal retireCharges,
    BigDecimal totalPensions,
    TripleAmountModel retirePatrimoine,
    TripleAmountModel fireRente,
    TripleAmountModel financialOnlyRente
) {}
