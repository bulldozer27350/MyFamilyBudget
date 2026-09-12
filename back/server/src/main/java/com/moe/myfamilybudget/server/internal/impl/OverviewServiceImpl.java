package com.moe.myfamilybudget.server.internal.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.calculation.OverviewCalculationService;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;

/**
 * Contrôleur REST de l'aperçu financier global (Overview) : orchestration HTTP uniquement (lecture
 * des données via {@link com.moe.myfamilybudget.server.internal.persistence.PersistenceManager},
 * mapping du résultat en DTO). Toute la logique de calcul vit désormais dans
 * {@link OverviewCalculationService} (point 8 de l'audit, {@code audit-mitigation-plan.md}) :
 * déplacement de code, comportement fonctionnel inchangé.
 */
@RestController
public class OverviewServiceImpl implements OverviewApi{

    private final OverviewMapper mapper;
    private final com.moe.myfamilybudget.server.internal.persistence.PersistenceManager persistenceManager;
    private final OverviewCalculationService calculationService;

    public OverviewServiceImpl(OverviewMapper mapper, com.moe.myfamilybudget.server.internal.persistence.PersistenceManager persistenceManager) {
        this.mapper = mapper;
        this.persistenceManager = persistenceManager;
        this.calculationService = new OverviewCalculationService();
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        BudgetDataModel internalData = this.persistenceManager.getBudgetData();
        OverviewResultModel internalResult = calculationService.computeOverview(internalData, Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toDto(internalResult));
    }

    /**
     * Conservé pour compatibilité avec les tests existants qui exercent directement le calcul de
     * projection de retraite d'une personne (voir {@code OverviewServiceImplTest}). Délègue
     * entièrement à {@link OverviewCalculationService#computeRetirementProjection}.
     */
    public OverviewCalculationService.RetirementProjection computeRetirementProjection(
            BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        return calculationService.computeRetirementProjection(data, person, retireYear);
    }
}
