package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.GoalReader;
import com.moe.myfamilybudget.server.internal.port.GoalWriter;

/**
 * Adaptateur de persistance pour {@link GoalReader} (RF-B00) et {@link GoalWriter} (DB-041).
 * Les ecritures passent encore par le {@code PersistenceManager} (liste {@code "objectifs"}) jusqu'a la
 * bascule JPA du domaine.
 */
@Component
public class GoalPersistenceAdapter implements GoalReader, GoalWriter {

    private static final String LIST_KEY = "objectifs";

    private final PersistenceManager persistenceManager;

    public GoalPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<ObjectifModel> getGoals() {
        return persistenceManager.getBudgetData().getEffectiveObjectifs();
    }

    @Override
    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        return persistenceManager.savePatrimoineRow(LIST_KEY, body);
    }

    @Override
    public void deleteGoalRow(String id) {
        persistenceManager.deletePatrimoineRow(LIST_KEY, id);
    }
}
