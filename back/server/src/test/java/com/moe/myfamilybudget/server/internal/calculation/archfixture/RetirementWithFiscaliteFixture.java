package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationService;

/** Fixture ARCH-020 fautive : un « moteur » Retraite qui dépend de l'implémentation Fiscalité. */
public abstract class RetirementWithFiscaliteFixture {
    protected TaxCalculationService dependency;
}
