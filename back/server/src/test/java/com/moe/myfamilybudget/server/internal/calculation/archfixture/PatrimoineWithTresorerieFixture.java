package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;

/** Fixture ARCH-020 fautive : un « moteur » Patrimoine qui dépend de Trésorerie. */
public abstract class PatrimoineWithTresorerieFixture {
    protected TresorerieCalculationService dependency;
}
