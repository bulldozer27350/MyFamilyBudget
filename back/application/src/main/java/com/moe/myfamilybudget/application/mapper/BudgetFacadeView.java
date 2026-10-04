package com.moe.myfamilybudget.application.mapper;

import java.util.List;

import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
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
 * cette vue. SILO-115 : elle ne connaît plus {@code BudgetDataModel} ; le service Analyse l'assemble
 * directement depuis les ports de lecture, et {@code OverviewMapper.facadeViewOf} porte la conversion
 * depuis le snapshot global jusqu'à SILO-119.
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

    /** Paramètres Objectifs absents : valeurs par défaut (contrat REST inchangé). */
    public BudgetFacadeView {
        if (objectifsParameters == null) {
            objectifsParameters = ObjectifsParameters.defaults();
        }
    }
}
