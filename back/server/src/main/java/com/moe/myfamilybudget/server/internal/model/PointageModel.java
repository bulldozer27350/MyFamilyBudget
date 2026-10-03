package com.moe.myfamilybudget.server.internal.model;

import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import java.util.Collections;
import java.util.List;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;

/**
 * Modèle de lecture (ViewModel) de la réponse {@code GET /pointage} : données brutes composées par la
 * couche application pour l'écran de pointage (le front calcule encore lui-même ses lignes du mois).
 *
 * <p>Depuis RF-501, ce n'est plus une entrée de calcul : aucun calculateur ne le consomme. Le moteur
 * de pointage ({@link PointageCalculator}) travaille sur
 * {@link com.moe.myfamilybudget.domain.bankpointage.calculation.PointageInput}, qui ne porte ni charges,
 * ni revenus, ni placements, ni paramètres. Ce modèle ne peut disparaître qu'avec une évolution du
 * contrat {@code GET /pointage}, hors périmètre de RF-501.
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
        if (settings == null) settings = new SettingsModel(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
