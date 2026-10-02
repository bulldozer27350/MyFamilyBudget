package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import org.springframework.web.client.RestTemplate;

/** Fixture ARCH-010 fautive : un « moteur » qui utilise Spring hors stéréotypes. */
public abstract class EngineWithSpringWebFixture {
    protected RestTemplate restTemplate;
}
