package com.moe.myfamilybudget.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SILO-200 -- configuration JPA commune aux silos")
class InfraJpaConfigurationTest {

    @Test
    @DisplayName("le customizer enregistre la stratégie de nommage des silos")
    void customizerRegistersSiloNamingStrategy() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, "valeur.initiale");

        new InfraJpaConfiguration().siloPhysicalNamingStrategyCustomizer().customize(properties);

        assertThat(properties)
                .containsEntry(AvailableSettings.PHYSICAL_NAMING_STRATEGY, SiloPhysicalNamingStrategy.class.getName());
    }

    @Test
    @DisplayName("la stratégie reste celle de Spring Boot : aucune règle de nommage surchargée")
    void namingStrategyKeepsSpringBootBehaviour() {
        assertThat(CamelCaseToUnderscoresNamingStrategy.class).isAssignableFrom(SiloPhysicalNamingStrategy.class);

        Method[] declared = SiloPhysicalNamingStrategy.class.getDeclaredMethods();
        assertThat(Arrays.stream(declared).map(Method::getName))
                .as("toute règle de nommage ajoutée ici change les noms de tables : elle exige un patch dédié")
                .isEmpty();
    }
}
