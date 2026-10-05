package com.moe.myfamilybudget.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moe.myfamilybudget.domain.analysis.calculation.AnalyseCalculationService;
import com.moe.myfamilybudget.domain.analysis.core.DefaultAnalyseCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.core.DefaultBankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.core.DefaultPointageCalculationService;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.credit.core.DefaultLoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.market.calculation.MarketDataService;
import com.moe.myfamilybudget.domain.market.core.BdfMortgageRateClient;
import com.moe.myfamilybudget.domain.market.core.CdcRegulatedRatesClient;
import com.moe.myfamilybudget.domain.market.core.DefaultMarketDataService;
import com.moe.myfamilybudget.domain.market.core.EcbYieldCurveClient;
import com.moe.myfamilybudget.domain.market.port.MarketSnapshotStore;
import com.moe.myfamilybudget.domain.market.port.MortgageRateProvider;
import com.moe.myfamilybudget.domain.market.port.RegulatedRatesProvider;
import com.moe.myfamilybudget.domain.market.port.YieldCurveProvider;
import com.moe.myfamilybudget.domain.notifications.core.BalanceFloorRule;
import com.moe.myfamilybudget.domain.notifications.core.DebitThresholdRule;
import com.moe.myfamilybudget.domain.notifications.core.ObjectifReachableRule;
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
 * Déclaration des beans Spring des moteurs de domaine extraits en modules Maven (MAVEN-020, MAVEN-040, MAVEN-080, MAVEN-090). SILO-150 à SILO-159 : les moteurs Retraite, Fiscalité, Patrimoine,
 * Trésorerie, Banque/Pointage, Analyse et Crédit sont exposés sous leur interface ({@code retirement-api}, {@code tax-api},
 * {@code wealth-api}, {@code treasury-api}, {@code bank-pointage-api}, {@code analysis-api}, {@code credit-api}), leur implémentation vit dans
 * {@code retirement-core}, {@code tax-core}, {@code wealth-core}, {@code treasury-core}, {@code bank-pointage-core}
 * {@code analysis-core} et {@code credit-core}. Les trois règles de notification (SILO-158) implémentent
 * {@code NotificationRule} ({@code notifications-api}) et vivent dans {@code notifications-core}. Le silo Marché
 * (SILO-159) expose {@code MarketDataService} ({@code market-api}) ; ses clients HTTP et son implémentation vivent
 * dans {@code market-core} et reçoivent ici les propriétés {@code myfamilybudget.market-data.*}.
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
        return new DefaultLoanAdviceCalculationService();
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

    @Bean
    public RegulatedRatesProvider regulatedRatesProvider(
            @Value("${myfamilybudget.market-data.cdc.base-url:https://opendata.caissedesdepots.fr/api/explore/v2.1}") String baseUrl,
            @Value("${myfamilybudget.market-data.cdc.dataset:flux-et-taux-la-ldds-lep}") String dataset,
            @Value("${myfamilybudget.market-data.timeout-seconds:5}") int timeoutSeconds) {
        return new CdcRegulatedRatesClient(baseUrl, dataset, timeoutSeconds);
    }

    @Bean
    public MortgageRateProvider mortgageRateProvider(
            @Value("${myfamilybudget.market-data.bdf.base-url:https://webstat.banque-france.fr/api/explore/v2.1}") String baseUrl,
            @Value("${myfamilybudget.market-data.bdf.api-key:}") String apiKey,
            @Value("${myfamilybudget.market-data.bdf.mortgage-series-key:MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N}") String seriesKey,
            @Value("${myfamilybudget.market-data.timeout-seconds:5}") int timeoutSeconds) {
        return new BdfMortgageRateClient(baseUrl, apiKey, seriesKey, timeoutSeconds);
    }

    @Bean
    public YieldCurveProvider yieldCurveProvider(
            @Value("${myfamilybudget.market-data.ecb.base-url:https://data-api.ecb.europa.eu}") String baseUrl,
            @Value("${myfamilybudget.market-data.timeout-seconds:5}") int timeoutSeconds) {
        return new EcbYieldCurveClient(baseUrl, timeoutSeconds);
    }

    @Bean
    public MarketDataService marketDataService(RegulatedRatesProvider regulatedRatesProvider,
            MortgageRateProvider mortgageRateProvider, YieldCurveProvider yieldCurveProvider,
            MarketSnapshotStore store) {
        return new DefaultMarketDataService(regulatedRatesProvider, mortgageRateProvider, yieldCurveProvider, store);
    }
}
