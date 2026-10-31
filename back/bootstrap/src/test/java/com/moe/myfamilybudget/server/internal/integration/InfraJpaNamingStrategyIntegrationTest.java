package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.moe.myfamilybudget.infra.jpa.SiloPhysicalNamingStrategy;

import jakarta.persistence.EntityManagerFactory;

/**
 * SILO-200 -- la stratégie de nommage physique de {@code infra-jpa} est bien celle utilisée par Hibernate
 * dans l'application assemblée (le contexte démarre et le schéma est créé avec elle).
 */
@SpringBootTest
@DisplayName("SILO-200 -- stratégie de nommage physique d'infra-jpa")
class InfraJpaNamingStrategyIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Hibernate utilise SiloPhysicalNamingStrategy")
    void hibernateUsesSiloPhysicalNamingStrategy() {
        Object configured = entityManagerFactory.getProperties().get(AvailableSettings.PHYSICAL_NAMING_STRATEGY);

        assertThat(String.valueOf(configured)).contains(SiloPhysicalNamingStrategy.class.getName());
    }
}
