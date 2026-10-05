package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.persistence.entity.LoanEntity;

/** Fixture ARCH-010 fautive : un « moteur » qui connaît une entité de persistance. */
public abstract class EngineWithPersistenceEntityFixture {
    protected LoanEntity entity;
}
