package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;

/**
 * Port de lecture pour le domaine Budget / Tresorerie (RF-B00).
 */
public interface BudgetReader {

    List<IncomeModel> getIncomes();

    List<ChargeModel> getCharges();

    List<OneOffExpenseModel> getOneoffExpenses();

    List<VariableIncomeModel> getVariableIncomes();

    List<VariableOverrideModel> getVariableOverrides();
}