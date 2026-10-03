package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.application.service.RetraiteServiceImpl;

/** Fixture ARCH-020 fautive : un domaine qui dépend d'une implémentation d'API REST. */
public abstract class RetirementWithRestFacadeFixture {
    protected RetraiteServiceImpl dependency;
}
