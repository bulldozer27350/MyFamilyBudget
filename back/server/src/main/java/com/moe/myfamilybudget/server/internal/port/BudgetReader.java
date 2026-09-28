package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;

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