package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record OverviewResultModel(
    BudgetDataModel data,
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
