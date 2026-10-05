package com.moe.myfamilybudget.infra.jpa;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Hibernate commune aux silos (SILO-200).
 *
 * <p>Enregistre {@link SiloPhysicalNamingStrategy} comme stratégie de nommage physique. Le customizer est
 * appliqué par Spring Boot après les propriétés {@code spring.jpa.*} : la stratégie est donc la même pour
 * tous les silos, quel que soit le profil. Les entités et les repositories restent découverts par le scan
 * par défaut de Spring Boot jusqu'à SILO-210 (chaque cœur de silo déclarera alors son propre package).
 */
@Configuration(proxyBeanMethods = false)
public class InfraJpaConfiguration {

    @Bean
    public HibernatePropertiesCustomizer siloPhysicalNamingStrategyCustomizer() {
        return properties -> properties.put(
                AvailableSettings.PHYSICAL_NAMING_STRATEGY, SiloPhysicalNamingStrategy.class.getName());
    }
}
