package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.TresorerieApi;
import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.server.internal.calculation.TresorerieCalculationService;
import com.moe.myfamilybudget.server.internal.mapper.TresorerieMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.CategoryOptionModel;
import com.moe.myfamilybudget.server.internal.model.TresorerieResultModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Contrôleur REST de la trésorerie prévisionnelle (Trésorerie) : orchestration HTTP uniquement
 * (lecture/écriture via {@link PersistenceManager}, mapping du résultat en DTO). Toute la logique
 * de calcul vit désormais dans {@link TresorerieCalculationService} (point 8 de l'audit,
 * {@code audit-mitigation-plan.md}) : déplacement de code, comportement fonctionnel inchangé.
 */
@RestController
public class TresorerieServiceImpl implements TresorerieApi {

    private final TresorerieMapper mapper;
    private final PersistenceManager persistenceManager;
    private final TresorerieCalculationService calculationService;

    public TresorerieServiceImpl(TresorerieMapper mapper, PersistenceManager persistenceManager) {
        this.mapper = mapper;
        this.persistenceManager = persistenceManager;
        this.calculationService = new TresorerieCalculationService();
    }

    @Override
    public ResponseEntity<TresorerieResponseDto> getTresorerie(Boolean useConstantEuros) {
        BudgetDataModel data = this.persistenceManager.getBudgetData();
        TresorerieResultModel result = calculationService.computeTresorerie(data, Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toTresorerieResponseDto(result));
    }

    @Override
    public ResponseEntity<Object> addTresorerieLigne(String listKey, Object body) {
        Map<String, Object> created = this.persistenceManager.addTresorerieRow(listKey, (Map<String, Object>)body);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Void> updateTresorerieLigne(String listKey, String id, com.moe.myfamilybudget.api.model.UpdateTresorerieLigneRequestDto body) {
        if (body != null) {
            this.persistenceManager.updateTresorerieRow(listKey, id, body.getField(), body.getValue());
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeTresorerieLigne(String listKey, String id) {
        this.persistenceManager.removeTresorerieRow(listKey, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> applyTresorerieAjustement(TresorerieAjustementRequestDto request) {
        if (request != null && request.getLineId() != null && request.getKind() != null && request.getNewMonthly() != null) {
            BigDecimal newMonthly = BigDecimal.valueOf(request.getNewMonthly().doubleValue());
            this.persistenceManager.applyTresorerieAjustement(request.getLineId(), request.getKind(), newMonthly);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Conservé pour compatibilité avec {@code TresorerieServiceImplTest}, qui exerce directement
     * le calcul. Délègue entièrement à {@link TresorerieCalculationService#computeTresorerie}.
     */
    public TresorerieResultModel computeTresorerie(BudgetDataModel data, boolean useConstantEuros) {
        return calculationService.computeTresorerie(data, useConstantEuros);
    }

    /**
     * Conservé pour compatibilité avec {@code TresorerieServiceImplTest}. Délègue entièrement à
     * {@link TresorerieCalculationService#buildCategoryOptions}.
     */
    public List<CategoryOptionModel> buildCategoryOptions(BudgetDataModel data) {
        return calculationService.buildCategoryOptions(data);
    }
}
