package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.BudgetReader;

/**
 * Adaptateur de persistance pour {@link BudgetReader} (RF-B00).
 */
@Component
public class BudgetPersistenceAdapter implements BudgetReader {

    private final PersistenceManager persistenceManager;

    public BudgetPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<IncomeModel> getIncomes() {
        return persistenceManager.getBudgetData().getEffectiveIncomes();
    }

    @Override
    public List<ChargeModel> getCharges() {
        return persistenceManager.getBudgetData().getEffectiveCharges();
    }

    @Override
    public List<OneOffExpenseModel> getOneoffExpenses() {
        return persistenceManager.getBudgetData().getEffectiveOneoff();
    }

    @Override
    public List<VariableIncomeModel> getVariableIncomes() {
        return persistenceManager.getBudgetData().getEffectiveVariableIncomes();
    }

    @Override
    public List<VariableOverrideModel> getVariableOverrides() {
        return persistenceManager.getBudgetData().getEffectiveVariableOverrides();
    }
}