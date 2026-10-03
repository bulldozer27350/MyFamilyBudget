package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;

/** Fixture ARCH-010 fautive : un « moteur » qui connaît le snapshot global. */
public abstract class EngineWithBudgetDataModelFixture {
    protected BudgetDataModel data;
}
