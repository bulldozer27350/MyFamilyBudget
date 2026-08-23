package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record BudgetDataModel(
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
    List<AssetCategoryModel> assetCategories
) {
 // Thread-safe static reference to preserve custom asset categories across record copies/updates.
    private static final java.util.concurrent.atomic.AtomicReference<List<AssetCategoryModel>> activeCategories =
        new java.util.concurrent.atomic.AtomicReference<>(List.of());

    public BudgetDataModel {
        if (assetCategories != null) {
            activeCategories.set(assetCategories);
        }
    }
    public BudgetDataModel(
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
        BankImportModel bankImport
    ) {
        this(settings, incomes, charges, placements, realEstate, retirement, taxChildren,
             taxBrackets, taxRateOverrides, taxActualOverrides, oneoff, transfers,
             variableIncomes, variableOverrides, bankImport,
             activeCategories.get() != null ? activeCategories.get() : List.of());
    }

    public SettingsModel getEffectiveSettings() {
        if (settings != null) {
            return settings;
        }
        return new SettingsModel(
            1985, 64, 85, new BigDecimal("0.02"), "", "manual",
            BigDecimal.ZERO, 21, new BigDecimal("0.10"),
            new BigDecimal("47100"), new BigDecimal("0.015")
        );
    }

    public List<AssetCategoryModel> getEffectiveAssetCategories() {
        return assetCategories != null ? assetCategories : List.of();
    }

    public List<IncomeModel> getEffectiveIncomes() {
        return incomes != null ? incomes : List.of();
    }

    public List<ChargeModel> getEffectiveCharges() {
        return charges != null ? charges : List.of();
    }

    public List<PlacementModel> getEffectivePlacements() {
        return placements != null ? placements : List.of();
    }

    public List<RealEstateModel> getEffectiveRealEstate() {
        return realEstate != null ? realEstate : List.of();
    }

    public List<TaxChildModel> getEffectiveTaxChildren() {
        return taxChildren != null ? taxChildren : List.of();
    }

    public List<TaxBracketModel> getEffectiveTaxBrackets() {
        if (taxBrackets != null && !taxBrackets.isEmpty()) {
            return taxBrackets;
        }
        return List.of(
            new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
            new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
            new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
            new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
            new TaxBracketModel("tb_5", null, new BigDecimal("0.45"))
        );
    }

    public List<TaxRateOverrideModel> getEffectiveTaxRateOverrides() {
        return taxRateOverrides != null ? taxRateOverrides : List.of();
    }

    public List<TaxActualOverrideModel> getEffectiveTaxActualOverrides() {
        return taxActualOverrides != null ? taxActualOverrides : List.of();
    }

    public List<OneOffExpenseModel> getEffectiveOneoff() {
        return oneoff != null ? oneoff : List.of();
    }

    public List<TransferModel> getEffectiveTransfers() {
        return transfers != null ? transfers : List.of();
    }

    public List<VariableIncomeModel> getEffectiveVariableIncomes() {
        return variableIncomes != null ? variableIncomes : List.of();
    }

    public List<VariableOverrideModel> getEffectiveVariableOverrides() {
        return variableOverrides != null ? variableOverrides : List.of();
    }
}
