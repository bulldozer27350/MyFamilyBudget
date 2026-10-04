package com.moe.myfamilybudget.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.transition.port.SimulationSettingsReader;
import com.moe.myfamilybudget.transition.port.SimulationSettingsWriter;

/**
 * Adaptateur de persistance pour {@link SettingsReader} (RF-B00), {@link SimulationSettingsWriter} et
 * {@link EconomicAssumptionsWriter} (SET-020). Le stockage physique reste {@code SettingsEntity} pour tout
 * sauf les paramètres Objectifs ; sa séparation relève des patchs DB-xxx.
 *
 * <p>SILO-100 : l'adaptateur expose aussi les paramètres par propriétaire ({@link RetirementSettingsReader},
 * {@link TaxSettingsReader}, {@link TresorerieSettingsReader}, {@link SimulationSettingsReader},
 * {@link EconomicAssumptionsReader}). Ces lectures projettent le même état que {@link #getSettings()} ; les
 * consommateurs migrent un silo après l'autre (SILO-110 à SILO-118), puis {@link SettingsReader} disparaît.
 * L'adaptateur reste le seul lecteur du cache jusqu'à SILO-220 (stockage chez les propriétaires).
 */
@Component
public class SettingsPersistenceAdapter
        implements SettingsReader, RetirementSettingsReader, TaxSettingsReader, TresorerieSettingsReader,
        SimulationSettingsReader, EconomicAssumptionsReader, SimulationSettingsWriter, EconomicAssumptionsWriter {

    private final PersistenceManager persistenceManager;

    public SettingsPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public SettingsModel getSettings() {
        return persistenceManager.getBudgetData().getEffectiveSettings();
    }

    @Override
    public RetirementSettingsModel getRetirementSettings() {
        SettingsModel s = getSettings();
        return new RetirementSettingsModel(s.birthYear(), s.retireAge());
    }

    @Override
    public TaxSettingsModel getTaxSettings() {
        SettingsModel s = getSettings();
        return new TaxSettingsModel(s.childExitAge(), s.taxAbattement());
    }

    @Override
    public TresorerieSettingsModel getTresorerieSettings() {
        SettingsModel s = getSettings();
        return new TresorerieSettingsModel(s.pivotDate(), s.pivotMode(), s.startBalance(), s.sweepEnabled(),
                s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold());
    }

    @Override
    public SimulationSettingsModel getSimulationSettings() {
        return new SimulationSettingsModel(getSettings().simulateUntilAge());
    }

    @Override
    public EconomicAssumptionsModel getEconomicAssumptions() {
        return new EconomicAssumptionsModel(getSettings().inflationRate());
    }

    @Override
    public void updateSimulateUntilAge(Object value) {
        persistenceManager.write(m -> m.updateSimulateUntilAge(value));
    }

    @Override
    public void updateInflationRate(Object value) {
        persistenceManager.write(m -> m.updateInflationRate(value));
    }
}