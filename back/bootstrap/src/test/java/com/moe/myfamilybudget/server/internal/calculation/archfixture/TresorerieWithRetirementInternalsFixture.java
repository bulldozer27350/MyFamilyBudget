package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;

/** Fixture ARCH-020 fautive : un « moteur » Trésorerie qui dépend du moteur Retraite plutôt que de sa projection. */
public abstract class TresorerieWithRetirementInternalsFixture {
    protected RetirementCalculationService dependency;
}
