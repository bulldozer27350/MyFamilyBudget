package com.moe.myfamilybudget.application.mapper;

import java.util.List;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.transition.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;

/**
 * SILO-119 (lot B2) : décomposition d'un {@code BudgetDataDto} importé en fragments, un par silo. Remplace
 * le passage par {@code BudgetDataModel} ({@code OverviewMapper#toInternalModel}).
 *
 * <p>Une section absente du JSON donne une liste vide ; un bloc de paramètres absent ({@code settings}
 * absent) donne {@code null}, que les ports {@code replace} lisent comme « valeur par défaut ».
 */
public record BudgetSnapshotFragments(
        RetirementSettingsModel retirementSettings,
        RetirementModel retirement,
        TaxSettingsModel taxSettings,
        List<TaxChildModel> taxChildren,
        List<TaxBracketModel> taxBrackets,
        List<TaxRateOverrideModel> taxRateOverrides,
        List<TaxActualOverrideModel> taxActualOverrides,
        TresorerieSettingsModel tresorerieSettings,
        List<IncomeModel> incomes,
        List<ChargeModel> charges,
        List<OneOffExpenseModel> oneoff,
        List<VariableIncomeModel> variableIncomes,
        List<VariableOverrideModel> variableOverrides,
        SimulationSettingsModel simulationSettings,
        EconomicAssumptionsModel economicAssumptions,
        List<PlacementModel> placements,
        List<RealEstateModel> realEstate,
        List<PatrimoineTransferModel> transfers,
        List<AssetCategoryModel> assetCategories,
        List<LoanModel> loans,
        List<ObjectifModel> goals,
        BankImportModel bankImport,
        ObjectifsParameters objectifsParameters) {
}
