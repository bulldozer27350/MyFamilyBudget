package com.moe.myfamilybudget.server.internal.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SystemeApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@RestController
public class SystemeServiceImpl implements SystemeApi {

    private final PersistenceManager persistenceManager;
    private final OverviewMapper overviewMapper;
    private final ObjectifsSettingsService objectifsSettingsService;

    public SystemeServiceImpl(PersistenceManager persistenceManager, OverviewMapper overviewMapper,
            ObjectifsSettingsService objectifsSettingsService) {
        this.persistenceManager = persistenceManager;
        this.overviewMapper = overviewMapper;
        this.objectifsSettingsService = objectifsSettingsService;
    }

    @Override
    public ResponseEntity<BudgetDataDto> getBudgetFull() {
        BudgetDataModel model = persistenceManager.getBudgetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(model, objectifsSettingsService.current()));
    }

    @Override
    public ResponseEntity<BudgetDataDto> importJSON(BudgetDataDto body) {
        if (body != null) {
            BudgetDataModel model = overviewMapper.toInternalModel(body);
            persistenceManager.setBudgetData(model);
            objectifsSettingsService.save(overviewMapper.toObjectifsParameters(body));
        }
        BudgetDataModel current = persistenceManager.getBudgetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(current, objectifsSettingsService.current()));
    }

    @Override
    public ResponseEntity<BudgetDataDto> resetData() {
        BudgetDataModel reset = persistenceManager.resetData();
        objectifsSettingsService.reset();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(reset, objectifsSettingsService.current()));
    }
}
