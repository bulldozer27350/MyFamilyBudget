package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.application.overview.OverviewCalculationService;

/** Fixture ARCH-020 fautive : un domaine qui dépend d'Overview, agrégateur final. */
public abstract class RetirementWithOverviewFixture {
    protected OverviewCalculationService dependency;
}
