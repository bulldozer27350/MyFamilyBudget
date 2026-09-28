package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.GoalReader;

/**
 * Adaptateur de persistance pour {@link GoalReader} (RF-B00).
 */
@Component
public class GoalPersistenceAdapter implements GoalReader {

    private final PersistenceManager persistenceManager;

    public GoalPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<ObjectifModel> getGoals() {
        return persistenceManager.getBudgetData().getEffectiveObjectifs();
    }
}