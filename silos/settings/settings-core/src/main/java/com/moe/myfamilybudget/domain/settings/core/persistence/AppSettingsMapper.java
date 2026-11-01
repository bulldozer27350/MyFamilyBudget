package com.moe.myfamilybudget.domain.settings.core.persistence;

import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;

/**
 * Mapper entre les modèles du silo Paramètres ({@link SimulationSettingsModel}, {@link EconomicAssumptionsModel})
 * et {@link AppSettingsEntity} (SILO-220, lot A2).
 *
 * <p>Conversion sans perte : aucun défaut {@code getEffective*} n'est appliqué (une valeur absente reste
 * {@code null}). Les deux notions ont chacune leur port : {@code toEntity} compose les deux modèles
 * (l'un ou l'autre peut être {@code null}), {@code toSimulation} et {@code toEconomicAssumptions} les relisent.
 */
public final class AppSettingsMapper {

    private AppSettingsMapper() {}

    public static AppSettingsEntity toEntity(SimulationSettingsModel simulation, EconomicAssumptionsModel economics) {
        AppSettingsEntity entity = new AppSettingsEntity();
        entity.setSimulateUntilAge(simulation != null ? simulation.simulateUntilAge() : null);
        entity.setInflationRate(economics != null ? economics.inflationRate() : null);
        return entity;
    }

    public static SimulationSettingsModel toSimulation(AppSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SimulationSettingsModel(entity.getSimulateUntilAge());
    }

    public static EconomicAssumptionsModel toEconomicAssumptions(AppSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new EconomicAssumptionsModel(entity.getInflationRate());
    }
}
