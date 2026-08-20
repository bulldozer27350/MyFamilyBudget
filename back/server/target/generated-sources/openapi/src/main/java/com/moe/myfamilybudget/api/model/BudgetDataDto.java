package com.moe.myfamilybudget.api.model;

import java.util.List;

public record BudgetDataDto(
    SettingsDto settings,
    List<IncomeDto> incomes,
    List<ChargeDto> charges,
    List<PlacementDto> placements,
    List<RealEstateDto> realEstate,
    RetirementDto retirement,
    List<TaxChildDto> taxChildren,
    List<TaxBracketDto> taxBrackets,
    List<TaxRateOverrideDto> taxRateOverrides,
    List<TaxActualOverrideDto> taxActualOverrides,
    List<OneOffExpenseDto> oneoff,
    List<TransferDto> transfers,
    List<VariableIncomeDto> variableIncomes,
    List<VariableOverrideDto> variableOverrides,
    BankImportDto bankImport
) {}
