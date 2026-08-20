package com.moe.myfamilybudget.api.model;

import java.math.BigDecimal;
import java.util.List;

public record OverviewResponseDto(
    BudgetDataDto data,
    List<Integer> years,
    List<CashflowYearDto> cashflow,
    PatrimoineProjectionsDto patrimoine,
    boolean useConstantEuros,
    int retireYear,
    BigDecimal pivotBalance,
    BigDecimal patrimoineActuel,
    BigDecimal fluxNetActuel,
    BigDecimal retireCharges,
    BigDecimal totalPensions,
    TripleAmountDto retirePatrimoine,
    TripleAmountDto fireRente,
    TripleAmountDto financialOnlyRente
) {}
