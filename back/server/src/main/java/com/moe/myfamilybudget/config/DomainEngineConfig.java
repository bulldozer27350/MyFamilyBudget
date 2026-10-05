package com.moe.myfamilybudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.notifications.rules.BalanceFloorRule;
import com.moe.myfamilybudget.domain.notifications.rules.DebitThresholdRule;
import com.moe.myfamilybudget.domain.notifications.rules.ObjectifReachableRule;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;

/**
 * Déclaration des beans Spring des moteurs de domaine extraits en modules Maven (MAVEN-020, MAVEN-040, MAVEN-080, MAVEN-090). SILO-150 : le moteur Retraite est exposé sous son interface
 * ({@code retirement-api}), son implémentation vit dans {@code retirement-core}.
 *
 * <p>Les modules de domaine sont volontairement indépendants de Spring : leurs moteurs ne portent
 * plus {@code @Component}, c'est le composition root qui les expose comme beans.
 */
@Configuration(proxyBeanMethods = false)
public class DomainEngineConfig {

    @Bean
    public RetirementCalculationService retirementCalculationService() {
        return new DefaultRetirementCalculationService();
    }

    @Bean
    public PatrimoineProjectionService patrimoineProjectionService() {
        return new PatrimoineProjectionService();
    }

    @Bean
    public PlacementEvolutionService placementEvolutionService() {
        return new PlacementEvolutionService();
    }

    @Bean
    public LoanAdviceCalculationService loanAdviceCalculationService() {
        return new LoanAdviceCalculationService();
    }

    @Bean
    public DebitThresholdRule debitThresholdRule() {
        return new DebitThresholdRule();
    }

    @Bean
    public BalanceFloorRule balanceFloorRule() {
        return new BalanceFloorRule();
    }

    @Bean
    public ObjectifReachableRule objectifReachableRule() {
        return new ObjectifReachableRule();
    }
}
