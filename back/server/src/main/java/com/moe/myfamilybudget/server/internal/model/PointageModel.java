package com.moe.myfamilybudget.server.internal.model;

import java.util.Collections;
import java.util.List;

/**
 * Modèle de domaine pur représentant l'état du pointage mensuel.
 * Indépendant de tout DTO OpenAPI ou framework REST.
 */
public record PointageModel(
        List<BankImportModel.BankTransactionModel> transactions,
        List<BankImportModel.CategoryModel> categories,
        List<BankImportModel.MatchingModel> matchings,
        List<ChargeModel> charges,
        List<IncomeModel> incomes,
        List<PlacementModel> placements,
        SettingsModel settings
) {
    public PointageModel {
        if (transactions == null) transactions = Collections.emptyList();
        if (categories == null) categories = Collections.emptyList();
        if (matchings == null) matchings = Collections.emptyList();
        if (charges == null) charges = Collections.emptyList();
        if (incomes == null) incomes = Collections.emptyList();
        if (placements == null) placements = Collections.emptyList();
        if (settings == null) settings = new SettingsModel(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
