package com.moe.myfamilybudget.server.internal.model;

import java.util.List;
import com.moe.myfamilybudget.domain.budget.CashflowYearModel;
import com.moe.myfamilybudget.domain.budget.CategoryOptionModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;
import com.moe.myfamilybudget.domain.budget.VariablePreviewModel;

public record TresorerieResultModel(
    List<IncomeModel> incomes,
    List<ChargeModel> charges,
    List<OneOffExpenseModel> oneoff,
    List<VariableIncomeModel> variableIncomes,
    List<VariableOverrideModel> variableOverrides,
    List<String> incomeLabels,
    List<String> variableIncomeLabels,
    List<CategoryOptionModel> categoryOptions,
    List<TresorerieSuggestionModel> suggestions,
    int retireYear,
    List<Integer> years,
    List<CashflowYearModel> cashflow,
    List<VariablePreviewModel> variablePreview,
    List<Integer> previewYears
) {}
