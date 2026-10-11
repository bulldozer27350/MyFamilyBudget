package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.List;

import com.moe.myfamilybudget.domain.tax.core.persistence.JpaTaxStore;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSnapshotWriter;
import com.moe.myfamilybudget.domain.tax.port.TaxWriter;

/**
 * Silo Fiscalite en memoire pour les tests unitaires (R-42, lot B) : le vrai {@link JpaTaxStore} adosse a des
 * {@link InMemoryTaxRepository}, sans base ni {@code PersistenceManager}. Remplace l'ancien
 * {@code TaxPersistenceAdapter}.
 */
public final class InMemoryTaxStore implements TaxReader, TaxWriter, TaxSettingsReader, TaxSnapshotWriter {

    private final JpaTaxStore delegate = new JpaTaxStore(
            InMemoryTaxRepository.createChildRepository(),
            InMemoryTaxRepository.createBracketRepository(),
            InMemoryTaxRepository.createRateOverrideRepository(),
            InMemoryTaxRepository.createActualOverrideRepository(),
            InMemoryTaxRepository.createSettingsRepository(),
            event -> { });

    public JpaTaxStore getDelegate() {
        return delegate;
    }

    @Override
    public List<TaxChildModel> getTaxChildren() {
        return delegate.getTaxChildren();
    }

    @Override
    public List<TaxBracketModel> getTaxBrackets() {
        return delegate.getTaxBrackets();
    }

    @Override
    public List<TaxRateOverrideModel> getTaxRateOverrides() {
        return delegate.getTaxRateOverrides();
    }

    @Override
    public List<TaxActualOverrideModel> getTaxActualOverrides() {
        return delegate.getTaxActualOverrides();
    }

    @Override
    public TaxSettingsModel getTaxSettings() {
        return delegate.getTaxSettings();
    }

    @Override
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        delegate.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
    }

    @Override
    public void updateTaxSettings(TaxSettingField field, Object value) {
        delegate.updateTaxSettings(field, value);
    }

    @Override
    public void resetDefaultTaxBrackets() {
        delegate.resetDefaultTaxBrackets();
    }

    @Override
    public void replace(TaxSettingsModel settings, List<TaxChildModel> children, List<TaxBracketModel> brackets,
                        List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        delegate.replace(settings, children, brackets, rateOverrides, actualOverrides);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
