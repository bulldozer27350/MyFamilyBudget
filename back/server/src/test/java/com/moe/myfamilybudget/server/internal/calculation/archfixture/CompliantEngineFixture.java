package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import org.springframework.stereotype.Component;

/**
 * Fixture ARCH-010 conforme : seul un stéréotype Spring est utilisé. Abstraite pour ne pas être
 * enregistrée comme bean par le scan de composants des tests Spring.
 */
@Component
public abstract class CompliantEngineFixture {
}
