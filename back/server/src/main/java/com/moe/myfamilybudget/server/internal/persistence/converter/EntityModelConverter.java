package com.moe.myfamilybudget.server.internal.persistence.converter;

import com.moe.myfamilybudget.server.internal.model.*;
import com.moe.myfamilybudget.server.internal.persistence.entity.*;

import java.util.List;
import java.util.stream.Collectors;

public class EntityModelConverter {

    // Settings conversions
    public static SettingsEntity toEntity(SettingsModel model) {
        if (model == null) return null;
        SettingsEntity entity = new SettingsEntity(
            model.birthYear(),
            model.retireAge(),
            model.simulateUntilAge(),
            model.inflationRate(),
            model.pivotDate(),
            model.pivotMode(),
            model.startBalance(),
            model.childExitAge(),
            model.taxAbattement(),
            // SET-040 : colonnes heritees de SettingsEntity, plus ecrites (source unique : la retraite).
            // Leur suppression physique releve des patchs DB-xxx.
            null,
            null
        );
        entity.setSweepEnabled(model.sweepEnabled());
        entity.setCashCeiling(model.cashCeiling());
        entity.setCashFloor(model.cashFloor());
        entity.setCashAlertThreshold(model.cashAlertThreshold());
        return entity;
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
            entity.getSweepEnabled(),
            entity.getCashCeiling(),
            entity.getCashFloor(),
            entity.getCashAlertThreshold()
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
        entity.setSweepPriority(model.sweepPriority());
        entity.setSweepCap(model.sweepCap());
        entity.setPauseTriggerBalance(model.pauseTriggerBalance());
        entity.setPausePriority(model.pausePriority());
        entity.setCategoryId(model.categoryId());
        entity.setBudgetData(budgetData);

        // Convert history (valeurs reelles constatees)
        List<PlacementHistoryEntryEntity> history = model.getEffectiveHistory().stream()
            .map(h -> toEntity(h, entity))
            .collect(Collectors.toList());
        entity.setHistory(history);

        return entity;
    }

    public static PlacementModel toModel(PlacementEntity entity) {
        if (entity == null) return null;
        List<PlacementHistoryEntryModel> history = entity.getHistory().stream()
            .map(EntityModelConverter::toModel)
            .collect(Collectors.toList());
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
            entity.getNotes(),
            entity.getSweepPriority(),
            entity.getSweepCap(),
            entity.getPauseTriggerBalance(),
            entity.getPausePriority(),
            entity.getCategoryId(),
            history
        );
    }

    // PlacementHistoryEntry conversions
    public static PlacementHistoryEntryEntity toEntity(PlacementHistoryEntryModel model, PlacementEntity placement) {
        if (model == null) return null;
        PlacementHistoryEntryEntity entity = new PlacementHistoryEntryEntity(
            model.id(),
            model.date(),
            model.value(),
            model.notes()
        );
        entity.setPlacement(placement);
        return entity;
    }

    public static PlacementHistoryEntryModel toModel(PlacementHistoryEntryEntity entity) {
        if (entity == null) return null;
        return new PlacementHistoryEntryModel(
            entity.getUid(),
            entity.getDate(),
            entity.getValue(),
            entity.getNotes()
        );
    }

    // Loan conversions
    public static LoanEntity toEntity(LoanModel model, BudgetDataEntity budgetData) {
        if (model == null) return null;
        LoanEntity entity = new LoanEntity(
            model.id(),
            model.label(),
            model.crd(),
            model.rate(),
            model.monthly(),
            model.insurance(),
            model.startDate(),
            model.endDate(),
            model.initialAmount(),
            model.totalInstallments(),
            model.stepDate()
        );
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static LoanModel toModel(LoanEntity entity) {
        if (entity == null) return null;
        return new LoanModel(
            entity.getUid(),
            entity.getLabel(),
            entity.getCrd(),
            entity.getRate(),
            entity.getMonthly(),
            entity.getInsurance(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getInitialAmount(),
            entity.getTotalInstallments(),
            entity.getStepDate()
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
        entity.setType(model.type());
        entity.setNotes(model.notes());
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
            entity.getTaxable(),
            entity.getType(),
            entity.getNotes()
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
        entity.setNotes(model.notes());
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
            entity.getTaxable(),
            entity.getNotes()
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
        entity.setColor(model.color());
        entity.setBudgetData(budgetData);
        return entity;
    }

    public static AssetCategoryModel toModel(AssetCategoryEntity entity) {
        if (entity == null) return null;
        return new AssetCategoryModel(
            entity.getUid(),
            entity.getIcon(),
            entity.getName(),
            entity.getBucket(),
            entity.getColor()
        );
    }

    // BudgetData conversions
    public static BudgetDataEntity toEntity(BudgetDataModel model) {
        if (model == null) return null;
        
        BudgetDataEntity entity = new BudgetDataEntity();
        entity.setSettings(toEntity(model.settings()));
        // DB-1100 : la retraite n'est plus rattachee au hub ; elle est stockee dans les tables autonomes
        // pension_* (PensionPlanEntity), ecrites par BudgetPersistenceGateway#syncPension.
        
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
            null, // Retirement - lue depuis les tables autonomes pension_* (DB-1100)
            List.of(), // taxChildren - lues depuis les tables autonomes fiscal_* (DB-1110)
            List.of(),
            List.of(),
            List.of(),
            entity.getOneoff().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getTransfers().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getVariableIncomes().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getVariableOverrides().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            null, // BankImport - handled separately due to JSON serialization
            entity.getAssetCategories().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            entity.getLoans().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            List.of() // objectifs - lus depuis les tables autonomes goal_* (DB-1120)
        );
    }
}
