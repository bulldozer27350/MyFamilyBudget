package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.server.internal.calculation.PlacementCashflowInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementIncomeProjection;
import com.moe.myfamilybudget.server.internal.calculation.TaxProjection;

/**
 * Fixture ARCH-020 conforme : Trésorerie consomme uniquement les projections explicites des autres
 * domaines.
 */
public abstract class TresorerieWithProjectionsFixture {
    protected RetirementIncomeProjection retirement;
    protected TaxProjection tax;
    protected PlacementCashflowInput placement;
}
