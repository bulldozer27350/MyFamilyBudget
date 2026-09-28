package com.moe.myfamilybudget.server.internal.impl;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.calculation.OverviewCalculationService;
import com.moe.myfamilybudget.server.internal.calculation.OverviewInput;
import com.moe.myfamilybudget.server.internal.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.server.internal.factory.OverviewInputFactory;
import com.moe.myfamilybudget.server.internal.factory.RetirementInputFactory;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Contrôleur REST de l'aperçu financier global (Overview) : orchestration HTTP uniquement (lecture
 * des données via {@link PersistenceManager}, composition de l'{@link OverviewInput} via
 * {@link OverviewInputFactory}, délégation au moteur {@link OverviewCalculationService}, et
 * mapping du résultat en DTO).
 */
@RestController
public class OverviewServiceImpl implements OverviewApi {

    private final OverviewMapper mapper;
    private final PersistenceManager persistenceManager;
    private final OverviewInputFactory inputFactory;
    private final OverviewCalculationService calculationService;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;

    public OverviewServiceImpl(OverviewMapper mapper, PersistenceManager persistenceManager) {
        this.mapper = mapper;
        this.persistenceManager = persistenceManager;
        this.inputFactory = new OverviewInputFactory();
        this.calculationService = new OverviewCalculationService();
        this.retirementInputFactory = new RetirementInputFactory();
        this.retirementCalculationService = new RetirementCalculationService();
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        BudgetDataModel internalData = this.persistenceManager.getBudgetData();
        OverviewInput input = inputFactory.from(internalData, Boolean.TRUE.equals(useConstantEuros));
        OverviewResultModel internalResult = calculationService.computeOverview(input);
        return ResponseEntity.ok(this.mapper.toDto(internalResult, internalData));
    }

    /**
     * Conservé pour compatibilité avec les tests existants qui exercent directement le calcul de
     * projection de retraite d'une personne (voir {@code OverviewServiceImplTest} et
     * {@code BusinessLogicIntegrationTest}). Délègue entièrement au moteur de calcul centralisé
     * {@link RetirementCalculationService} via {@link RetirementInputFactory}.
     */
    public RetirementProjectionModel computeRetirementProjection(
            BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        RetirementModel retirement = data.retirement();
        List<RetirementModel.RetirementPersonModel> people = retirement != null ? retirement.getEffectivePeople() : List.of();
        int index = people.indexOf(person);
        RetirementProjection projection = retirementCalculationService.compute(retirementInputFactory.create(data));
        return index >= 0 && index < projection.people().size() ? projection.people().get(index) : null;
    }
}
