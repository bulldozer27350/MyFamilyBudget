package com.moe.myfamilybudget.server.internal.dto;

import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import java.math.BigDecimal;
import java.util.List;

public record OverviewResponseDto(
    BudgetDataModel data,
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
