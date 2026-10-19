package com.moe.myfamilybudget.server.internal.testsupport;

import com.moe.myfamilybudget.domain.retirement.core.persistence.JpaRetirementStore;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSnapshotWriter;
import com.moe.myfamilybudget.domain.retirement.port.RetirementWriter;

/**
 * Silo Retraite en memoire pour les tests unitaires (R-40, lot B) : le vrai {@link JpaRetirementStore} adosse
 * a des {@link InMemoryRetirementRepository}, sans base ni {@code PersistenceManager}. Remplace l'ancien
 * {@code RetirementPersistenceAdapter}.
 */
public final class InMemoryRetirementStore
        implements RetirementReader, RetirementWriter, RetirementSettingsReader, RetirementSnapshotWriter {

    private final JpaRetirementStore delegate = new JpaRetirementStore(
            InMemoryRetirementRepository.createPlanRepository(),
            InMemoryRetirementRepository.createSettingsRepository(),
            event -> { });

    public JpaRetirementStore getDelegate() {
        return delegate;
    }

    @Override
    public RetirementModel getRetirement() {
        return delegate.getRetirement();
    }

    @Override
    public RetirementSettingsModel getRetirementSettings() {
        return delegate.getRetirementSettings();
    }

    @Override
    public void updateRetirement(RetirementModel retirement) {
        delegate.updateRetirement(retirement);
    }

    @Override
    public void updateRetirementSetting(RetirementSettingField field, Object value) {
        delegate.updateRetirementSetting(field, value);
    }

    @Override
    public void replace(RetirementSettingsModel settings, RetirementModel retirement) {
        delegate.replace(settings, retirement);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
