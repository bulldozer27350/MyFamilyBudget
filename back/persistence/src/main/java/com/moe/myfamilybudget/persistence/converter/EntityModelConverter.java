package com.moe.myfamilybudget.persistence.converter;

import java.util.List;
import java.util.stream.Collectors;

import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.persistence.entity.BudgetDataEntity;
import com.moe.myfamilybudget.persistence.entity.PlacementEntity;
import com.moe.myfamilybudget.persistence.entity.PlacementHistoryEntryEntity;
import com.moe.myfamilybudget.persistence.entity.SettingsEntity;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;

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
            List.of(), // revenus - lus depuis cashflow_income (SILO-216)
            List.of(), // charges - lues depuis cashflow_charge (SILO-216)
            entity.getPlacements().stream().map(EntityModelConverter::toModel).collect(Collectors.toList()),
            List.of(), // immobilier - lu depuis wealth_real_estate (SILO-215, R-30b)
            null, // Retirement - lue depuis les tables autonomes pension_* (DB-1100)
            List.of(), // taxChildren - lues depuis les tables autonomes fiscal_* (DB-1110)
            List.of(),
            List.of(),
            List.of(),
            List.of(), // oneoff - lus depuis cashflow_oneoff (SILO-216)
            List.of(), // transfers - lus depuis cashflow_transfer (SILO-216, DA-14)
            List.of(), // variableIncomes - lus depuis cashflow_variable_income (SILO-216)
            List.of(), // variableOverrides - lus depuis cashflow_variable_override (SILO-216)
            null, // BankImport - handled separately due to JSON serialization
            List.of(), // categories d'actifs - lues depuis wealth_asset_category (SILO-215, R-30b)
            List.of(), // prets - lus depuis la table autonome credit_loan (SILO-214)
            List.of() // objectifs - lus depuis les tables autonomes goal_* (DB-1120)
        );
    }
}
