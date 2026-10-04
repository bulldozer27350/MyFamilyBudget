package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.AnalysePretsApi;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresValuesDto;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceSettingsService;
import com.moe.myfamilybudget.application.factory.LoanAdviceInputFactory;
import com.moe.myfamilybudget.application.mapper.AnalysePretsMapper;
import com.moe.myfamilybudget.server.internal.marketdata.MarketDataService;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.MortgageRateQuote;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness;
import com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Contrôleur REST implémentant le contrat OpenAPI AnalysePretsApi (Tag: AnalysePrets) : façade
 * mince, tout le calcul est dans LoanAdviceCalculationService et les hypothèses modifiables dans
 * LoanAdviceSettingsService.
 *
 * Le taux de marché de la renégociation est choisi dans cet ordre : paramètre de requête (simulation),
 * taux saisi dans les hypothèses, puis taux moyen des nouveaux crédits de la Banque de France.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. SILO-116 : plus de {@code BudgetDataModel} ; {@link LoanAdviceInputFactory}
 * reçoit directement les prêts ({@link LoanReader}) et les placements et catégories d'actifs
 * ({@link PatrimoineReader}).
 */
@RestController
public class AnalysePretsServiceImpl implements AnalysePretsApi {

    private final LoanAdviceCalculationService calculationService;
    private final LoanAdviceSettingsService settingsService;
    private final MarketDataService marketDataService;
    private final AnalysePretsMapper mapper;
    private final LoanReader loanReader;
    private final PatrimoineReader patrimoineReader;

    public AnalysePretsServiceImpl(
            LoanAdviceCalculationService calculationService, LoanAdviceSettingsService settingsService,
            MarketDataService marketDataService, AnalysePretsMapper mapper,
            LoanReader loanReader, PatrimoineReader patrimoineReader) {
        this.calculationService = calculationService;
        this.settingsService = settingsService;
        this.marketDataService = marketDataService;
        this.mapper = mapper;
        this.loanReader = loanReader;
        this.patrimoineReader = patrimoineReader;
    }

    @Override
    public ResponseEntity<AnalysePretsDto> getAnalysePrets(BigDecimal marketRate) {
        LoanAdviceParameters saved = settingsService.current();
        ResolvedMarketRate resolved = resolveMarketRate(marketRate, saved, marketDataService.current());
        LoanAdviceParameters effective = withMarketRate(saved, resolved.rate());
        LoanAdviceResultModel result = calculationService.compute(
                LoanAdviceInputFactory.from(loanReader.getLoans(), patrimoineReader.getPlacements(),
                        patrimoineReader.getAssetCategories(), effective, resolved.rate(), LocalDate.now()));
        // Le calcul ignore un taux hors plage : l'origine n'est alors pas affichée.
        LoanAdviceResultModel described = result.marketRateUsed() != null
                ? result.withMarketRateSource(resolved.source())
                : result;
        return ResponseEntity.ok(mapper.toDto(described));
    }

    @Override
    public ResponseEntity<AnalysePretsParametresDto> getAnalysePretsParametres() {
        return ResponseEntity.ok(mapper.toParametresDto(settingsService.current(), settingsService.defaults()));
    }

    @Override
    public ResponseEntity<AnalysePretsParametresDto> updateAnalysePretsParametres(
            AnalysePretsParametresValuesDto body) {
        LoanAdviceParameters saved = settingsService.save(mapper.toParameters(body));
        return ResponseEntity.ok(mapper.toParametresDto(saved, settingsService.defaults()));
    }

    /** Taux de marché retenu et son origine, affichée avec les résultats. */
    record ResolvedMarketRate(BigDecimal rate, String source) {
    }

    static ResolvedMarketRate resolveMarketRate(BigDecimal requested, LoanAdviceParameters saved, MarketRatesView market) {
        if (requested != null) {
            return new ResolvedMarketRate(requested, "Paramètre de requête");
        }
        if (saved.marketRate() != null) {
            return new ResolvedMarketRate(saved.marketRate(), "Saisie manuelle");
        }
        MortgageRateQuote mortgage = market.mortgageRate();
        if (mortgage != null) {
            String stale = market.mortgageStatus() == RegulatedRateFreshness.Status.STALE ? ", donnée ancienne" : "";
            return new ResolvedMarketRate(mortgage.rate(),
                    "Banque de France, " + mortgage.asOf().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)) + stale);
        }
        return new ResolvedMarketRate(null, null);
    }

    private static LoanAdviceParameters withMarketRate(LoanAdviceParameters p, BigDecimal marketRate) {
        return new LoanAdviceParameters(marketRate, p.repayMarginRate(), p.renegotiationMinGapRate(),
                p.renegotiationMinCrd(), p.renegotiationMinRemainingMonths(), p.renegotiationFixedCosts(),
                p.flatTaxRate());
    }
}
