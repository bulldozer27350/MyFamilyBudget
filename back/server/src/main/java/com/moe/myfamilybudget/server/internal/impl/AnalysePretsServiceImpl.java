package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.AnalysePretsApi;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresValuesDto;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceSettingsService;
import com.moe.myfamilybudget.server.internal.mapper.AnalysePretsMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Contrôleur REST implémentant le contrat OpenAPI AnalysePretsApi (Tag: AnalysePrets) : façade
 * mince, tout le calcul est dans LoanAdviceCalculationService et les hypothèses modifiables dans
 * LoanAdviceSettingsService.
 */
@RestController
public class AnalysePretsServiceImpl implements AnalysePretsApi {

    private final PersistenceManager persistenceManager;
    private final LoanAdviceCalculationService calculationService;
    private final LoanAdviceSettingsService settingsService;
    private final AnalysePretsMapper mapper;

    public AnalysePretsServiceImpl(PersistenceManager persistenceManager,
            LoanAdviceCalculationService calculationService, LoanAdviceSettingsService settingsService,
            AnalysePretsMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.calculationService = calculationService;
        this.settingsService = settingsService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<AnalysePretsDto> getAnalysePrets(BigDecimal marketRate) {
        BudgetDataModel data = persistenceManager.getBudgetData();
        LoanAdviceParameters saved = settingsService.current();
        // Un taux fourni dans la requête remplace ponctuellement celui enregistré (simulation).
        LoanAdviceParameters effective = marketRate == null ? saved : withMarketRate(saved, marketRate);
        LoanAdviceResultModel result = calculationService.compute(
                data.getEffectiveLoans(),
                data.getEffectivePlacements(),
                data.getEffectiveAssetCategories(),
                effective,
                LocalDate.now());
        return ResponseEntity.ok(mapper.toDto(result));
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

    private static LoanAdviceParameters withMarketRate(LoanAdviceParameters p, BigDecimal marketRate) {
        return new LoanAdviceParameters(marketRate, p.repayMarginRate(), p.renegotiationMinGapRate(),
                p.renegotiationMinCrd(), p.renegotiationMinRemainingMonths(), p.renegotiationFixedCosts(),
                p.flatTaxRate());
    }
}
