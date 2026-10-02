package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import jakarta.persistence.EntityManager;

/** Fixture ARCH-010 fautive : un « moteur » qui utilise l'API JPA. */
public abstract class EngineWithJpaApiFixture {
    protected EntityManager entityManager;
}
