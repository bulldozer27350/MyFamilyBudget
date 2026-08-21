package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

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
