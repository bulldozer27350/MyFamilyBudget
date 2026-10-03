package com.moe.myfamilybudget.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.transition.port.GlobalBudgetSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link GlobalBudgetSnapshotWriter} (MAVEN-103) : delegue a
 * {@link PersistenceManager#setBudgetData} et {@link PersistenceManager#resetData}. Il rejoint la transaction
 * ouverte par le composant de snapshot global.
 */
@Component
public class GlobalBudgetSnapshotWriterAdapter implements GlobalBudgetSnapshotWriter {

    private final PersistenceManager persistenceManager;

    public GlobalBudgetSnapshotWriterAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void setBudgetData(BudgetDataModel data) {
        persistenceManager.setBudgetData(data);
    }

    @Override
    public BudgetDataModel resetData() {
        return persistenceManager.resetData();
    }
}
