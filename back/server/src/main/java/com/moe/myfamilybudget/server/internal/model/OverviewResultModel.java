package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;
import com.moe.myfamilybudget.domain.budget.CashflowYearModel;
import com.moe.myfamilybudget.domain.budget.TripleAmountModel;

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
