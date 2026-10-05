package com.moe.myfamilybudget.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalEntityMapper;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalWriter;
import com.moe.myfamilybudget.domain.goals.port.GoalSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link GoalReader} (RF-B00) et {@link GoalWriter} (DB-041).
 *
 * <p>DB-1021 : en production, la lecture passe par {@link GoalRepository} (tables autonomes {@code goal} /
 * {@code goal_allocation}, DB-1020). Les ecritures passent toujours par le {@code PersistenceManager}
 * (liste {@code "objectifs"}, validation des allocations contre le cache) : la passerelle de persistance
 * recopie les objectifs dans les tables autonomes dans la meme transaction.
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class GoalPersistenceAdapter implements GoalReader, GoalWriter, GoalSnapshotWriter {

    private static final String LIST_KEY = "objectifs";

    private final PersistenceManager persistenceManager;
    private final GoalRepository goalRepository;

    public GoalPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null);
    }

    @Autowired
    public GoalPersistenceAdapter(PersistenceManager persistenceManager, GoalRepository goalRepository) {
        this.persistenceManager = persistenceManager;
        this.goalRepository = goalRepository;
    }

    @Override
    public List<ObjectifModel> getGoals() {
        if (goalRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveObjectifs();
        }
        return GoalEntityMapper.toModels(goalRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.savePatrimoineRow(LIST_KEY, body));
    }

    @Override
    public void deleteGoalRow(String id) {
        persistenceManager.write(m -> m.deletePatrimoineRow(LIST_KEY, id));
    }

    /** SILO-119 (lot B1) : import des objectifs. */
    @Override
    public void replace(List<ObjectifModel> goals) {
        persistenceManager.write(m -> m.replaceGoalsSnapshot(goals));
    }

    /** SILO-119 (lot B1) : suppression de tous les objectifs. */
    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetGoalsSnapshot());
    }
}
