package com.moe.myfamilybudget.persistence.adapter;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.converter.CashflowEntityMapper;
import com.moe.myfamilybudget.persistence.repository.CashflowChargeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowOneOffRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.transition.port.BudgetReader;

/**
 * Adaptateur de persistance pour {@link BudgetReader} (RF-B00).
 *
 * <p>DB-1061 : en production, la lecture des revenus, charges, depenses ponctuelles, revenus variables et
 * surcharges annuelles passe par les repositories autonomes {@code cashflow_*} (DB-1060). Les ecritures du
 * domaine passent par {@code TresoreriePersistenceAdapter} et le {@code PersistenceManager} : la passerelle de
 * persistance recopie les lignes dans les tables autonomes dans la meme transaction.
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class BudgetPersistenceAdapter implements BudgetReader {

    private final PersistenceManager persistenceManager;
    private final CashflowIncomeRepository cashflowIncomeRepository;
    private final CashflowChargeRepository cashflowChargeRepository;
    private final CashflowOneOffRepository cashflowOneOffRepository;
    private final CashflowVariableIncomeRepository cashflowVariableIncomeRepository;
    private final CashflowVariableOverrideRepository cashflowVariableOverrideRepository;

    public BudgetPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null, null, null, null, null);
    }

    @Autowired
    public BudgetPersistenceAdapter(PersistenceManager persistenceManager,
                                    CashflowIncomeRepository cashflowIncomeRepository,
                                    CashflowChargeRepository cashflowChargeRepository,
                                    CashflowOneOffRepository cashflowOneOffRepository,
                                    CashflowVariableIncomeRepository cashflowVariableIncomeRepository,
                                    CashflowVariableOverrideRepository cashflowVariableOverrideRepository) {
        this.persistenceManager = persistenceManager;
        this.cashflowIncomeRepository = cashflowIncomeRepository;
        this.cashflowChargeRepository = cashflowChargeRepository;
        this.cashflowOneOffRepository = cashflowOneOffRepository;
        this.cashflowVariableIncomeRepository = cashflowVariableIncomeRepository;
        this.cashflowVariableOverrideRepository = cashflowVariableOverrideRepository;
    }

    @Override
    public List<IncomeModel> getIncomes() {
        if (cashflowIncomeRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveIncomes();
        }
        return CashflowEntityMapper.toIncomeModels(cashflowIncomeRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<ChargeModel> getCharges() {
        if (cashflowChargeRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveCharges();
        }
        return CashflowEntityMapper.toChargeModels(cashflowChargeRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<OneOffExpenseModel> getOneoffExpenses() {
        if (cashflowOneOffRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveOneoff();
        }
        return CashflowEntityMapper.toOneOffModels(cashflowOneOffRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<VariableIncomeModel> getVariableIncomes() {
        if (cashflowVariableIncomeRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveVariableIncomes();
        }
        return CashflowEntityMapper.toVariableIncomeModels(
                cashflowVariableIncomeRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public List<VariableOverrideModel> getVariableOverrides() {
        if (cashflowVariableOverrideRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveVariableOverrides();
        }
        return CashflowEntityMapper.toVariableOverrideModels(
                cashflowVariableOverrideRepository.findAllByOrderByPositionAsc());
    }
}
