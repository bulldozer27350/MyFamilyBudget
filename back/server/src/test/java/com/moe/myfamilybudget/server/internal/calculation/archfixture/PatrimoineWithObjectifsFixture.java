package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;

/** Fixture ARCH-020 fautive : un « moteur » Patrimoine qui dépend d'Objectifs. */
public abstract class PatrimoineWithObjectifsFixture {
    protected ObjectifModel dependency;
}
