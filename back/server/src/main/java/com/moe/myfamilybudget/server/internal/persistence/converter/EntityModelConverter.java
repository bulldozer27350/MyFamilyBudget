package com.moe.myfamilybudget.server.internal.persistence.converter;

import com.moe.myfamilybudget.server.internal.model.*;
import com.moe.myfamilybudget.server.internal.persistence.entity.*;

import java.util.List;
import java.util.stream.Collectors;

public class EntityModelConverter {

    // Settings conversions
    public static SettingsEntity toEntity(SettingsModel model) {
        if (model == null) return null;
        return new SettingsEntity(
            model.birthYear(),
            model.retireAge(),
            model.simulateUntilAge(),
            model.inflationRate(),
            model.pivotDate(),
            model.pivotMode(),
            model.startBalance(),
            model.childExitAge(),
            model.taxAbattement(),
            model.pass2026(),
            model.passGrowthRate()
        );
    }

    public static SettingsModel toModel(SettingsEntity entity) {
        if (entity == null) return null;
        return new SettingsModel(
            entity.getBirthYear(),
            entity.getRetireAge(),
            entity.getSimulateUntilAge(),
            entity.getInflationRate(),
            entity.getPivotDate(),
            entity.getPivotMode(),
            entity.getStartBalance(),
            entity.getChildExitAge(),
            entity.getTaxAbattement(),
            entity.getPass2026(),
            entity.getPassGrowthRate()
        );
    }

    // Income conversions
    public static IncomeEntity toEntity(IncomeModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        IncomeEntity entity = new IncomeEntity(
            model.id(),
            model.label(),
            model.monthly(),
            model.start(),
            model.end(),
            model.growthRate(),
            model.categoryId(),
            model.notes()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static IncomeModel toModel(IncomeEntity entity) {
        if (entity == null) return null;
        return new IncomeModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getMonthly(),
            entity.getStart(),
            entity.getEnd(),
            entity.getGrowthRate(),
            entity.getCategoryId(),
            entity.getNotes()
        );
    }

    // Charge conversions
    public static ChargeEntity toEntity(ChargeModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        ChargeEntity entity = new ChargeEntity(
            model.id(),
            model.label(),
            model.monthly(),
            model.start(),
            model.end(),
            model.growthRate(),
            model.categoryId(),
            model.notes()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static ChargeModel toModel(ChargeEntity entity) {
        if (entity == null) return null;
        return new ChargeModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getMonthly(),
            entity.getStart(),
            entity.getEnd(),
            entity.getGrowthRate(),
            entity.getCategoryId(),
            entity.getNotes()
        );
    }

    // Placement conversions
    public static PlacementEntity toEntity(PlacementModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        PlacementEntity entity = new PlacementEntity(
            model.id(),
            model.label(),
            model.category(),
            model.balance(),
            model.balanceDate(),
            model.monthly(),
            model.monthlyFrom(),
            model.monthlyUntil(),
            model.ratePess(),
            model.rateCorr(),
            model.rateOpti(),
            model.excludedFromRetirement(),
            model.notes()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static PlacementModel toModel(PlacementEntity entity) {
        if (entity == null) return null;
        return new PlacementModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getCategory(),
            entity.getBalance(),
            entity.getBalanceDate(),
            entity.getMonthly(),
            entity.getMonthlyFrom(),
            entity.getMonthlyUntil(),
            entity.getRatePess(),
            entity.getRateCorr(),
            entity.getRateOpti(),
            entity.getExcludedFromRetirement(),
            entity.getNotes()
        );
    }

    // RealEstate conversions
    public static RealEstateEntity toEntity(RealEstateModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        RealEstateEntity entity = new RealEstateEntity(
            model.id(),
            model.label(),
            model.type(),
            model.currentValue(),
            model.valuationYear(),
            model.annualGrowthRate(),
            model.notes()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static RealEstateModel toModel(RealEstateEntity entity) {
        if (entity == null) return null;
        return new RealEstateModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getType(),
            entity.getCurrentValue(),
            entity.getValuationYear(),
            entity.getAnnualGrowthRate(),
            entity.getNotes()
        );
    }

    // OneOffExpense conversions
    public static OneOffExpenseEntity toEntity(OneOffExpenseModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        OneOffExpenseEntity entity = new OneOffExpenseEntity(
            model.id(),
            model.label(),
            model.date(),
            model.amount(),
            model.notes()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static OneOffExpenseModel toModel(OneOffExpenseEntity entity) {
        if (entity == null) return null;
        return new OneOffExpenseModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getDate(),
            entity.getAmount(),
            entity.getNotes()
        );
    }

    // Transfer conversions
    public static TransferEntity toEntity(TransferModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        TransferEntity entity = new TransferEntity(
            model.id(),
            model.placement(),
            model.date(),
            model.amount(),
            model.notes()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static TransferModel toModel(TransferEntity entity) {
        if (entity == null) return null;
        return new TransferModel(
            entity.getUid(),
            entity.getPlacement(),
            entity.getDate(),
            entity.getAmount(),
            entity.getNotes()
        );
    }

    // VariableIncome conversions
    public static VariableIncomeEntity toEntity(VariableIncomeModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        VariableIncomeEntity entity = new VariableIncomeEntity(
            model.id(),
            model.label(),
            model.refIncomeLabel(),
            model.rate(),
            model.startYear(),
            model.endYear(),
            model.taxable()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static VariableIncomeModel toModel(VariableIncomeEntity entity) {
        if (entity == null) return null;
        return new VariableIncomeModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getRefIncomeLabel(),
            entity.getRate(),
            entity.getStartYear(),
            entity.getEndYear(),
            entity.getTaxable()
        );
    }

    // VariableOverride conversions
    public static VariableOverrideEntity toEntity(VariableOverrideModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        VariableOverrideEntity entity = new VariableOverrideEntity(
            model.id(),
            model.label(),
            model.year(),
            model.amount(),
            model.taxable()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static VariableOverrideModel toModel(VariableOverrideEntity entity) {
        if (entity == null) return null;
        return new VariableOverrideModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getYear(),
            entity.getAmount(),
            entity.getTaxable()
        );
    }

    // TaxChild conversions
    public static TaxChildEntity toEntity(TaxChildModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        TaxChildEntity entity = new TaxChildEntity(
            model.id(),
            model.name(),
            model.birthYear()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static TaxChildModel toModel(TaxChildEntity entity) {
        if (entity == null) return null;
        return new TaxChildModel(
            entity.getUid(),
            entity.getName(),
            entity.getBirthYear()
        );
    }

    // TaxBracket conversions
    public static TaxBracketEntity toEntity(TaxBracketModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        TaxBracketEntity entity = new TaxBracketEntity(
            model.id(),
            model.upTo(),
            model.rate()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static TaxBracketModel toModel(TaxBracketEntity entity) {
        if (entity == null) return null;
        return new TaxBracketModel(
            entity.getUid(),
            entity.getUpTo(),
            entity.getRate()
        );
    }

    // TaxRateOverride conversions
    public static TaxRateOverrideEntity toEntity(TaxRateOverrideModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        TaxRateOverrideEntity entity = new TaxRateOverrideEntity(
            model.year(),
            model.rate()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static TaxRateOverrideModel toModel(TaxRateOverrideEntity entity) {
        if (entity == null) return null;
        return new TaxRateOverrideModel(
            entity.getYear(),
            entity.getRate()
        );
    }

    // TaxActualOverride conversions
    public static TaxActualOverrideEntity toEntity(TaxActualOverrideModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        TaxActualOverrideEntity entity = new TaxActualOverrideEntity(
            model.year(),
            model.amount()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static TaxActualOverrideModel toModel(TaxActualOverrideEntity entity) {
        if (entity == null) return null;
        return new TaxActualOverrideModel(
            entity.getYear(),
            entity.getAmount()
        );
    }

    // AssetCategory conversions
    public static AssetCategoryEntity toEntity(AssetCategoryModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        AssetCategoryEntity entity = new AssetCategoryEntity(
            model.id(),
            model.icon(),
            model.name(),
            model.bucket()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static AssetCategoryModel toModel(AssetCategoryEntity entity) {
        if (entity == null) return null;
        return new AssetCategoryModel(
            entity.getUid(),
            entity.getIcon(),
            entity.getName(),
            entity.getBucket()
        );
    }

    // Retirement conversions
    public static RetirementEntity toEntity(RetirementModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        RetirementEntity entity = new RetirementEntity(
            model.pass2026(),
            model.passGrowthRate(),
            model.agircPointValue(),
            model.agircPointDateGlobal(),
            model.agircPointGrowthRate()
        );
        entity.setBudgetData(budgetData);
        
        // Convert people
        List<RetirementPersonEntity> people = model.people().stream()
            .map(person -> toEntity(person, entity))
            .collect(Collectors.toList());
        entity.setPeople(people);
        
        return entity;
    }

    public static RetirementModel toModel(RetirementEntity entity) {
        if (entity == null) return null;
        
        List<RetirementModel.RetirementPersonModel> people = entity.getPeople().stream()
            .map(EntityModelConverter::toModel)
            .collect(Collectors.toList());
        
        return new RetirementModel(
            people,
            entity.getPass2026(),
            entity.getPassGrowthRate(),
            entity.getAgircPointValue(),
            entity.getAgircPointDateGlobal(),
            entity.getAgircPointGrowthRate()
        );
    }

    // RetirementPerson conversions
    public static RetirementPersonEntity toEntity(RetirementModel.RetirementPersonModel model, RetirementEntity retirement) {
        if (model == null) return null;
        RetirementPersonEntity entity = new RetirementPersonEntity(
            model.id(),
            model.name(),
            model.birthYear(),
            model.incomeLabel(),
            model.trimestresValides(),
            model.trimestresDate(),
            model.agircPoints(),
            model.ratioPointsParEuro()
        );
        entity.setRetirement(retirement);
        
        // Convert salary history
        List<SalaryHistoryEntity> salaryHistory = model.salaryHistory().stream()
            .map(sh -> toEntity(sh, entity))
            .collect(Collectors.toList());
        entity.setSalaryHistory(salaryHistory);
        
        return entity;
    }

    public static RetirementModel.RetirementPersonModel toModel(RetirementPersonEntity entity) {
        if (entity == null) return null;
        
        List<RetirementModel.SalaryHistoryModel> salaryHistory = entity.getSalaryHistory().stream()
            .map(EntityModelConverter::toModel)
            .collect(Collectors.toList());
        
        return new RetirementModel.RetirementPersonModel(
            entity.getUid(),
            entity.getName(),
            entity.getBirthYear(),
            entity.getIncomeLabel(),
            entity.getTrimestresValides(),
            entity.getTrimestresDate(),
            salaryHistory,
            entity.getAgircPoints(),
            entity.getRatioPointsParEuro()
        );
    }

    // SalaryHistory conversions
    public static SalaryHistoryEntity toEntity(RetirementModel.SalaryHistoryModel model, RetirementPersonEntity retirementPerson) {
        if (model == null) return null;
        SalaryHistoryEntity entity = new SalaryHistoryEntity(
            model.year(),
            model.salary()
        );
        entity.setRetirementPerson(retirementPerson);
        return entity;
    }

    public static RetirementModel.SalaryHistoryModel toModel(SalaryHistoryEntity entity) {
        if (entity == null) return null;
        return new RetirementModel.SalaryHistoryModel(
            entity.getYear(),
            entity.getSalary()
        );
    }

    // BudgetData conversions
    public static BudgetDataEntity toEntity(BudgetDataModel model) {
        if (model == null) return null;
        
        BudgetDataEntity entity = new BudgetDataEntity();
        entity.setSettings(toEntity(model.settings()));
        
        // Lists will be set separately with proper budgetData references
        return entity;
    }

    public static BudgetDataModel toModel(BudgetDataEntity entity) {
        if (entity == null) return null;
        
        return new BudgetDataModel(
            toModel(entity.getSettings()),
            entity.getIncomes().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getCharges().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getPlacements().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getRealEstate().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            toModel(entity.getRetirement()),
            entity.getTaxChildren().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getTaxBrackets().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getTaxRateOverrides().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getTaxActualOverrides().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getOneoff().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getTransfers().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getVariableIncomes().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getVariableOverrides().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            null, // BankImport - handled separately due to JSON serialization
            entity.getAssetCategories().stream().map(EntityModelConverter::toModel).collect(Collectors.toList())
        );
    }
}
