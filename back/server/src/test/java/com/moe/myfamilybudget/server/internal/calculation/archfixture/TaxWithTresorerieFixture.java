package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.server.internal.calculation.TresorerieCalculationService;

/** Fixture ARCH-020 fautive : un « moteur » Fiscalité qui dépend de Trésorerie. */
public abstract class TaxWithTresorerieFixture {
    protected TresorerieCalculationService dependency;
}
