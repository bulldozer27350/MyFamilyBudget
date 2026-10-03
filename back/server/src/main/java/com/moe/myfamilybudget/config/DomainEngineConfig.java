package com.moe.myfamilybudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;

/**
 * Déclaration des beans Spring des moteurs de domaine extraits en modules Maven (MAVEN-020, MAVEN-040).
 *
 * <p>Les modules de domaine sont volontairement indépendants de Spring : leurs moteurs ne portent
 * plus {@code @Component}, c'est le composition root qui les expose comme beans.
 */
@Configuration(proxyBeanMethods = false)
public class DomainEngineConfig {

    @Bean
    public RetirementCalculationService retirementCalculationService() {
        return new RetirementCalculationService();
    }

    @Bean
    public PatrimoineProjectionService patrimoineProjectionService() {
        return new PatrimoineProjectionService();
    }

    @Bean
    public PlacementEvolutionService placementEvolutionService() {
        return new PlacementEvolutionService();
    }
}
