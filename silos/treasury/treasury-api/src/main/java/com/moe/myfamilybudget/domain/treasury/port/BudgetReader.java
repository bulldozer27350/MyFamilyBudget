package com.moe.myfamilybudget.domain.treasury.port;

import java.util.List;

import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;

/**
 * Port de lecture pour le domaine Tresorerie (SILO-216, lot B). Deplace depuis
 * {@code transition-snapshot} vers {@code treasury-api}.
 */
public interface BudgetReader {

    List<IncomeModel> getIncomes();

    List<ChargeModel> getCharges();

    List<OneOffExpenseModel> getOneoffExpenses();

    List<VariableIncomeModel> getVariableIncomes();

    List<VariableOverrideModel> getVariableOverrides();

    List<TransferModel> getTransfers();
}
