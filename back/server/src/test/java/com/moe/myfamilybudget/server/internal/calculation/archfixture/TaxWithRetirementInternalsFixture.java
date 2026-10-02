package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.server.internal.calculation.RetirementCalculationService;

/** Fixture ARCH-020 fautive : un « moteur » Fiscalité qui dépend du moteur Retraite plutôt que de sa projection. */
public abstract class TaxWithRetirementInternalsFixture {
    protected RetirementCalculationService dependency;
}
