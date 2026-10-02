package com.moe.myfamilybudget.server.internal.mapper;

import java.util.List;

import com.moe.myfamilybudget.server.internal.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;

/**
 * Vue de façade API (RES-010) : contenu du budget réexposé tel quel dans les réponses composites
 * {@code /overview} et {@code /analyse} ({@code data}, {@code settings}, {@code incomes}...).
 *
 * <p>Ce n'est ni un résultat métier ni le snapshot global : les mappers Overview/Analyse
 * reçoivent cette vue plutôt que {@code BudgetDataModel}, afin que le snapshot global ne serve
 * plus de raccourci dans leurs signatures. Les paramètres du domaine Objectifs (RF-700) sont
 * portés explicitement pour être réinjectés dans {@code settings} sans changer le contrat REST.
 *
 * <p>Seuls les assemblers de façade (services d'API, {@code GlobalBudgetSnapshotService}) construisent
 * cette vue, via {@link #from(BudgetDataModel, ObjectifsParameters)}.
 */
public record BudgetFacadeView(
        SettingsModel settings,
        List<IncomeModel> incomes,
        List<ChargeModel> charges,
        List<PlacementModel> placements,
        List<RealEstateModel> realEstate,
        RetirementModel retirement,
        List<TaxChildModel> taxChildren,
        List<TaxBracketModel> taxBrackets,
        List<TaxRateOverrideModel> taxRateOverrides,
        List<TaxActualOverrideModel> taxActualOverrides,
        List<OneOffExpenseModel> oneoff,
        List<TransferModel> transfers,
        List<VariableIncomeModel> variableIncomes,
        List<VariableOverrideModel> variableOverrides,
        BankImportModel bankImport,
        List<AssetCategoryModel> assetCategories,
        List<LoanModel> loans,
        List<ObjectifModel> objectifs,
        ObjectifsParameters objectifsParameters) {

    /** Assemble la vue de façade à partir du snapshot courant et des paramètres Objectifs. */
    public static BudgetFacadeView from(BudgetDataModel data, ObjectifsParameters objectifsParameters) {
        if (data == null) {
            return null;
        }
        return new BudgetFacadeView(
                data.settings(), data.incomes(), data.charges(), data.placements(), data.realEstate(),
                data.retirement(), data.taxChildren(), data.taxBrackets(), data.taxRateOverrides(),
                data.taxActualOverrides(), data.oneoff(), data.transfers(), data.variableIncomes(),
                data.variableOverrides(), data.bankImport(), data.assetCategories(), data.loans(),
                data.objectifs(),
                objectifsParameters != null ? objectifsParameters : ObjectifsParameters.defaults());
    }
}
