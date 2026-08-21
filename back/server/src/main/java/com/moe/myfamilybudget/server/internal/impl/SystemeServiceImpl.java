package com.moe.myfamilybudget.server.internal.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SystemeApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@RestController
public class SystemeServiceImpl implements SystemeApi {

    private final PersistenceManager persistenceManager;
    private final OverviewMapper overviewMapper;

    public SystemeServiceImpl(PersistenceManager persistenceManager, OverviewMapper overviewMapper) {
        this.persistenceManager = persistenceManager;
        this.overviewMapper = overviewMapper;
    }

    @Override
    public ResponseEntity<BudgetDataDto> getBudgetFull() {
        BudgetDataModel model = persistenceManager.getBudgetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(model));
    }

    @Override
    public ResponseEntity<BudgetDataDto> importJSON(BudgetDataDto body) {
        if (body != null) {
            BudgetDataModel model = overviewMapper.toInternalModel(body);
            persistenceManager.setBudgetData(model);
        }
        BudgetDataModel current = persistenceManager.getBudgetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(current));
    }

    @Override
    public ResponseEntity<BudgetDataDto> resetData() {
        BudgetDataModel reset = persistenceManager.resetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(reset));
    }
}
