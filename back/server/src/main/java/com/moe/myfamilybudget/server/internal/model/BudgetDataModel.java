package com.moe.myfamilybudget.server.internal.model;

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
    BankImportModel bankImport
) {
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
            new TaxBracketModel("tb_1", new java.math.BigDecimal("11294"), java.math.BigDecimal.ZERO),
            new TaxBracketModel("tb_2", new java.math.BigDecimal("28797"), new java.math.BigDecimal("0.11")),
            new TaxBracketModel("tb_3", new java.math.BigDecimal("82341"), new java.math.BigDecimal("0.30")),
            new TaxBracketModel("tb_4", new java.math.BigDecimal("177106"), new java.math.BigDecimal("0.41")),
            new TaxBracketModel("tb_5", null, new java.math.BigDecimal("0.45"))
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
