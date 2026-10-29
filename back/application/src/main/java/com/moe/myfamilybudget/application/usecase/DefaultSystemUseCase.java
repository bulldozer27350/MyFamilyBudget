package com.moe.myfamilybudget.application.usecase;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.application.snapshot.GlobalBudgetSnapshotService;
import com.moe.myfamilybudget.application.usecase.system.SystemUseCase;

/**
 * Implémentation du cas d'usage « Système » (R-88, SILO-319).
 *
 * <p>Orchestre les opérations globales sur l'ensemble des silos (export, import, réinitialisation)
 * en déléguant à {@link GlobalBudgetSnapshotService}.
 */
@Service
public class DefaultSystemUseCase implements SystemUseCase {

    private final GlobalBudgetSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    public DefaultSystemUseCase(GlobalBudgetSnapshotService snapshotService, ObjectMapper objectMapper) {
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> exportBudget() {
        BudgetDataDto dto = snapshotService.export();
        return objectMapper.convertValue(dto, Map.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> importBudget(Map<String, Object> snapshot) {
        BudgetDataDto body = snapshot == null ? null : objectMapper.convertValue(snapshot, BudgetDataDto.class);
        BudgetDataDto result = snapshotService.importSnapshot(body);
        return objectMapper.convertValue(result, Map.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> resetBudget() {
        BudgetDataDto result = snapshotService.reset();
        return objectMapper.convertValue(result, Map.class);
    }
}
