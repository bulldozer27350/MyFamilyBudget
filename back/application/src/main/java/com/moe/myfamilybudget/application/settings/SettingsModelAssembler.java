package com.moe.myfamilybudget.application.settings;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;

/**
 * SILO-100 (lot B) : compose le {@link SettingsModel} à partir des paramètres lus chez leurs propriétaires
 * (Retraite, Fiscalité, Trésorerie, Simulation, Hypothèses économiques). La persistance n'expose plus de
 * lecture globale des paramètres : l'assemblage est une responsabilité applicative.
 *
 * <p>Classe de transition : les services et factories qui consomment encore {@link SettingsReader} (parce que
 * {@code BudgetDataModel} contient un {@link SettingsModel}) migrent un silo après l'autre (SILO-110 à
 * SILO-118) vers les ports propriétaires ; cette classe et {@link SettingsReader} sont supprimés avec le
 * dernier consommateur (au plus tard SILO-120).
 */
@Component
public class SettingsModelAssembler implements SettingsReader {

    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;

    public SettingsModelAssembler(RetirementSettingsReader retirementSettingsReader,
                                  TaxSettingsReader taxSettingsReader,
                                  TresorerieSettingsReader tresorerieSettingsReader,
                                  SimulationSettingsReader simulationSettingsReader,
                                  EconomicAssumptionsReader economicAssumptionsReader) {
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
    }

    @Override
    public SettingsModel getSettings() {
        RetirementSettingsModel retirement = retirementSettingsReader.getRetirementSettings();
        TaxSettingsModel tax = taxSettingsReader.getTaxSettings();
        TresorerieSettingsModel tresorerie = tresorerieSettingsReader.getTresorerieSettings();
        SimulationSettingsModel simulation = simulationSettingsReader.getSimulationSettings();
        EconomicAssumptionsModel economic = economicAssumptionsReader.getEconomicAssumptions();
        return new SettingsModel(
                retirement.birthYear(), retirement.retireAge(),
                simulation.simulateUntilAge(), economic.inflationRate(),
                tresorerie.pivotDate(), tresorerie.pivotMode(), tresorerie.startBalance(),
                tax.childExitAge(), tax.taxAbattement(),
                tresorerie.sweepEnabled(), tresorerie.cashCeiling(), tresorerie.cashFloor(),
                tresorerie.cashAlertThreshold());
    }
}
