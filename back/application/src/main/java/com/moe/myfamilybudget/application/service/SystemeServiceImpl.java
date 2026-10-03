package com.moe.myfamilybudget.application.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SystemeApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.application.snapshot.GlobalBudgetSnapshotService;

/**
 * Façade REST des opérations transverses {@code /budget}, {@code /budget/import} et {@code /budget/reset}.
 *
 * <p>CLEAN-020 : ce contrôleur ne manipule plus {@code BudgetDataModel} ni {@code PersistenceManager} ; le snapshot
 * global (assemblage, import, reset, transactions) est isolé dans {@link GlobalBudgetSnapshotService}.
 */
@RestController
public class SystemeServiceImpl implements SystemeApi {

    private final GlobalBudgetSnapshotService snapshotService;

    public SystemeServiceImpl(GlobalBudgetSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Override
    public ResponseEntity<BudgetDataDto> getBudgetFull() {
        return ResponseEntity.ok(snapshotService.export());
    }

    @Override
    public ResponseEntity<BudgetDataDto> importJSON(BudgetDataDto body) {
        return ResponseEntity.ok(snapshotService.importSnapshot(body));
    }

    @Override
    public ResponseEntity<BudgetDataDto> resetData() {
        return ResponseEntity.ok(snapshotService.reset());
    }
}
