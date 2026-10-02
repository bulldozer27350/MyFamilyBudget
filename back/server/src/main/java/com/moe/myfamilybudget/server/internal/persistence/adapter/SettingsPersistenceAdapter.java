package com.moe.myfamilybudget.server.internal.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;
import com.moe.myfamilybudget.server.internal.port.SimulationSettingsWriter;

/**
 * Adaptateur de persistance pour {@link SettingsReader} (RF-B00), {@link SimulationSettingsWriter} et
 * {@link EconomicAssumptionsWriter} (SET-020). Le stockage physique reste {@code SettingsEntity} pour tout
 * sauf les paramètres Objectifs ; sa séparation relève des patchs DB-xxx.
 */
@Component
public class SettingsPersistenceAdapter
        implements SettingsReader, SimulationSettingsWriter, EconomicAssumptionsWriter {

    private final PersistenceManager persistenceManager;

    public SettingsPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public SettingsModel getSettings() {
        return persistenceManager.getBudgetData().getEffectiveSettings();
    }

    @Override
    public void updateSimulateUntilAge(Object value) {
        persistenceManager.write(m -> m.updateTaxSettings("simulateUntilAge", value));
    }

    @Override
    public void updateInflationRate(Object value) {
        persistenceManager.write(m -> m.updateTaxSettings("inflationRate", value));
    }
}