package com.moe.myfamilybudget.application.usecase.treasury;

import java.util.List;

/**
 * Modèle applicatif de la réponse Trésorerie : lignes (revenus, charges, dépenses ponctuelles,
 * revenus variables et leurs surcharges), options de catégorie, suggestions budgétaires et
 * projection de flux (R-82, mirroir de {@code domain.treasury.model.TresorerieResultModel}).
 */
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
