package com.moe.myfamilybudget.application.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.api.controller.SystemeApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.application.mapper.SystemeMapper;
import com.moe.myfamilybudget.application.snapshot.GlobalBudgetSnapshotService;
import com.moe.myfamilybudget.application.usecase.DefaultSystemUseCase;
import com.moe.myfamilybudget.application.usecase.system.SystemUseCase;

/**
 * Façade REST des opérations transverses {@code /budget}, {@code /budget/import} et {@code /budget/reset}.
 *
 * <p>R-88 (SILO-319) : ce contrôleur délègue entièrement au cas d'usage {@link SystemUseCase} et
 * ne conserve que la conversion HTTP ({@link ResponseEntity}, {@link BudgetDataDto}).
 */
@RestController
public class SystemeServiceImpl implements SystemeApi {

    private final SystemUseCase systemUseCase;
    private final SystemeMapper mapper;

    @Autowired
    public SystemeServiceImpl(SystemUseCase systemUseCase, SystemeMapper mapper) {
        this.systemUseCase = systemUseCase;
        this.mapper = mapper;
    }

    /**
     * Constructeur conservé pour la rétrocompatibilité des tests existants.
     */
    public SystemeServiceImpl(GlobalBudgetSnapshotService snapshotService) {
        ObjectMapper om = new ObjectMapper();
        this.systemUseCase = new DefaultSystemUseCase(snapshotService, om);
        this.mapper = new SystemeMapper(om);
    }

    @Override
    public ResponseEntity<BudgetDataDto> getBudgetFull() {
        return ResponseEntity.ok(mapper.toDto(systemUseCase.exportBudget()));
    }

    @Override
    public ResponseEntity<BudgetDataDto> importJSON(BudgetDataDto body) {
        return ResponseEntity.ok(mapper.toDto(systemUseCase.importBudget(mapper.toMap(body))));
    }

    @Override
    public ResponseEntity<BudgetDataDto> resetData() {
        return ResponseEntity.ok(mapper.toDto(systemUseCase.resetBudget()));
    }
}
