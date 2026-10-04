package com.moe.myfamilybudget.transition.port;

import java.util.List;

import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;

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