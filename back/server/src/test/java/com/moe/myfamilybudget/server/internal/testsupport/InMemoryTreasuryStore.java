package com.moe.myfamilybudget.server.internal.testsupport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSnapshotWriter;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieWriter;

/**
 * Silo Tresorerie en memoire pour les tests unitaires (SILO-216, lot B1) : le vrai {@link JpaTreasuryStore}
 * adosse a des {@link InMemoryTreasuryRepository}, sans base ni {@code PersistenceManager}. Remplace les anciens
 * {@code BudgetPersistenceAdapter} et {@code TresoreriePersistenceAdapter}.
 */
public final class InMemoryTreasuryStore implements BudgetReader, TresorerieWriter, TresorerieSnapshotWriter,
        com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader {

    private final JpaTreasuryStore delegate = new JpaTreasuryStore(
            InMemoryTreasuryRepository.createIncomeRepository(),
            InMemoryTreasuryRepository.createChargeRepository(),
            InMemoryTreasuryRepository.createOneOffRepository(),
            InMemoryTreasuryRepository.createTransferRepository(),
            InMemoryTreasuryRepository.createVariableIncomeRepository(),
            InMemoryTreasuryRepository.createVariableOverrideRepository(),
            InMemoryTreasuryRepository.createSettingsRepository(),
            event -> { });

    public JpaTreasuryStore getDelegate() {
        return delegate;
    }

    @Override
    public List<IncomeModel> getIncomes() {
        return delegate.getIncomes();
    }

    @Override
    public List<ChargeModel> getCharges() {
        return delegate.getCharges();
    }

    @Override
    public List<OneOffExpenseModel> getOneoffExpenses() {
        return delegate.getOneoffExpenses();
    }

    @Override
    public List<VariableIncomeModel> getVariableIncomes() {
        return delegate.getVariableIncomes();
    }

    @Override
    public List<VariableOverrideModel> getVariableOverrides() {
        return delegate.getVariableOverrides();
    }

    @Override
    public List<TransferModel> getTransfers() {
        return delegate.getTransfers();
    }

    @Override
    public com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel getTresorerieSettings() {
        return delegate.getTresorerieSettings();
    }

    @Override
    public Map<String, Object> addTresorerieRow(TresorerieList list, Map<String, Object> body) {
        return delegate.addTresorerieRow(list, body);
    }

    @Override
    public void updateTresorerieRow(TresorerieList list, String id, TresorerieLineField field, Object value) {
        delegate.updateTresorerieRow(list, id, field, value);
    }

    @Override
    public void removeTresorerieRow(TresorerieList list, String id) {
        delegate.removeTresorerieRow(list, id);
    }

    @Override
    public void applyTresorerieAjustement(String lineId, TresorerieAdjustmentKind kind, BigDecimal newMonthly) {
        delegate.applyTresorerieAjustement(lineId, kind, newMonthly);
    }

    @Override
    public void updateTresorerieSetting(TresorerieSettingField field, Object value) {
        delegate.updateTresorerieSetting(field, value);
    }

    @Override
    public void replace(TresorerieSettingsModel settings, List<IncomeModel> incomes, List<ChargeModel> charges,
                        List<OneOffExpenseModel> oneoffExpenses, List<VariableIncomeModel> variableIncomes,
                        List<VariableOverrideModel> variableOverrides, List<TransferModel> transfers) {
        delegate.replace(settings, incomes, charges, oneoffExpenses, variableIncomes, variableOverrides, transfers);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
