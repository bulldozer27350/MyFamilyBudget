package com.moe.myfamilybudget.application.usecase.overview;

import java.math.BigDecimal;
import java.util.List;

/**
 * Modèle applicatif de la réponse Overview (R-85, SILO-316).
 *
 * <p>Ne dépend ni de Spring, ni d'OpenAPI, ni des modèles internes des silos.
 */
public record OverviewResultModel(
    List<Integer> years,
    List<OverviewCashflowYearModel> cashflow,
    OverviewPatrimoineProjectionsModel patrimoine,
    boolean useConstantEuros,
    int retireYear,
    BigDecimal pivotBalance,
    BigDecimal patrimoineActuel,
    BigDecimal fluxNetActuel,
    BigDecimal retireCharges,
    BigDecimal totalPensions,
    OverviewTripleAmountModel retirePatrimoine,
    OverviewTripleAmountModel fireRente,
    OverviewTripleAmountModel financialOnlyRente
) {}
