package com.moe.myfamilybudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculationService;
import com.moe.myfamilybudget.domain.analysis.core.DefaultAnalyseCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.core.DefaultBankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.core.DefaultPointageCalculationService;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.notifications.rules.BalanceFloorRule;
import com.moe.myfamilybudget.domain.notifications.rules.DebitThresholdRule;
import com.moe.myfamilybudget.domain.notifications.rules.ObjectifReachableRule;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationService;
import com.moe.myfamilybudget.domain.tax.core.DefaultTaxCalculationService;
import com.moe.myfamilybudget.domain.treasury.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.domain.treasury.core.DefaultTresorerieCalculationService;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;
import com.moe.myfamilybudget.domain.wealth.core.DefaultPatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.core.DefaultPlacementEvolutionService;

/**
 * Déclaration des beans Spring des moteurs de domaine extraits en modules Maven (MAVEN-020, MAVEN-040, MAVEN-080, MAVEN-090). SILO-150 à SILO-155 : les moteurs Retraite, Fiscalité, Patrimoine,
 * Trésorerie, Banque/Pointage et Analyse sont exposés sous leur interface ({@code retirement-api}, {@code tax-api},
 * {@code wealth-api}, {@code treasury-api}, {@code bank-pointage-api}, {@code analysis-api}), leur implémentation vit dans
 * {@code retirement-core}, {@code tax-core}, {@code wealth-core}, {@code treasury-core}, {@code bank-pointage-core}
 * et {@code analysis-core}.
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
    public TaxCalculationService taxCalculationService() {
        return new DefaultTaxCalculationService();
    }

    @Bean
    public PatrimoineProjectionService patrimoineProjectionService() {
        return new DefaultPatrimoineProjectionService();
    }

    @Bean
    public PlacementEvolutionService placementEvolutionService() {
        return new DefaultPlacementEvolutionService();
    }

    @Bean
    public TresorerieCalculationService tresorerieCalculationService() {
        return new DefaultTresorerieCalculationService();
    }

    @Bean
    public BankImportCalculationService bankImportCalculationService() {
        return new DefaultBankImportCalculationService();
    }

    @Bean
    public PointageCalculationService pointageCalculationService() {
        return new DefaultPointageCalculationService();
    }

    @Bean
    public AnalyseCalculationService analyseCalculationService() {
        return new DefaultAnalyseCalculationService();
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
