package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculationService;

/** Fixture ARCH-020 fautive : un domaine qui dépend d'Analyse, consommateur final. */
public abstract class RetirementWithAnalyseFixture {
    protected AnalyseCalculationService dependency;
}
