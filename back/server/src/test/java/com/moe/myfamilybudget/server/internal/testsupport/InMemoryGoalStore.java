package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.List;
import java.util.Map;

import com.moe.myfamilybudget.domain.goals.core.persistence.JpaGoalStore;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalSnapshotWriter;
import com.moe.myfamilybudget.domain.goals.port.GoalWriter;

/**
 * Silo Objectifs en memoire pour les tests unitaires (SILO-212, lot B1) : le vrai {@link JpaGoalStore} (memes
 * valeurs par defaut, meme lecture du corps, meme ordre) adosse a un {@link InMemoryGoalRepository}, sans base
 * ni {@code PersistenceManager}. Remplace l'ancien {@code GoalPersistenceAdapter}, qui lisait le cache global.
 */
public final class InMemoryGoalStore implements GoalReader, GoalWriter, GoalSnapshotWriter {

    private final JpaGoalStore delegate = new JpaGoalStore(InMemoryGoalRepository.create(), event -> { });

    @Override
    public List<ObjectifModel> getGoals() {
        return delegate.getGoals();
    }

    @Override
    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        return delegate.saveGoalRow(body);
    }

    @Override
    public void deleteGoalRow(String id) {
        delegate.deleteGoalRow(id);
    }

    @Override
    public void replace(List<ObjectifModel> goals) {
        delegate.replace(goals);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
