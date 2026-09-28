package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Flux net d'une année, avant versements et retraits de placements (voir
 * {@link CashflowProjection}).
 *
 * @param year        année civile
 * @param netCashflow revenus - charges - dépenses ponctuelles de l'année ({@code null} vaut 0)
 */
public record AnnualCashflow(int year, BigDecimal netCashflow) {
    public AnnualCashflow {
        netCashflow = netCashflow != null ? netCashflow : BigDecimal.ZERO;
    }
}
