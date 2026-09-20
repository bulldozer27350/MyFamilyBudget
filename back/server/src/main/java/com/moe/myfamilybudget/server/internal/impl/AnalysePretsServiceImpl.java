package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.AnalysePretsApi;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.server.internal.mapper.AnalysePretsMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Contrôleur REST implémentant le contrat OpenAPI AnalysePretsApi (Tag: AnalysePrets) : façade
 * mince, tout le calcul est dans LoanAdviceCalculationService.
 */
@RestController
public class AnalysePretsServiceImpl implements AnalysePretsApi {

    private final PersistenceManager persistenceManager;
    private final LoanAdviceCalculationService calculationService;
    private final AnalysePretsMapper mapper;

    public AnalysePretsServiceImpl(PersistenceManager persistenceManager,
            LoanAdviceCalculationService calculationService, AnalysePretsMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.calculationService = calculationService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<AnalysePretsDto> getAnalysePrets(BigDecimal marketRate) {
        BudgetDataModel data = persistenceManager.getBudgetData();
        LoanAdviceResultModel result = calculationService.compute(
                data.getEffectiveLoans(),
                data.getEffectivePlacements(),
                data.getEffectiveAssetCategories(),
                LoanAdviceParameters.defaults(marketRate),
                LocalDate.now());
        return ResponseEntity.ok(mapper.toDto(result));
    }
}
