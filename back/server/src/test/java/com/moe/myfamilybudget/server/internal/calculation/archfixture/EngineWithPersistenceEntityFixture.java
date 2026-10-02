package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.server.internal.persistence.entity.LoanAdviceSettingsEntity;

/** Fixture ARCH-010 fautive : un « moteur » qui connaît une entité de persistance. */
public abstract class EngineWithPersistenceEntityFixture {
    protected LoanAdviceSettingsEntity entity;
}
