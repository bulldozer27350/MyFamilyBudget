package com.moe.myfamilybudget.application.settings;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

/**
 * R-52 : compose le {@link SettingsView} du bloc {@code settings} des réponses REST à partir des
 * paramètres déjà lus chez leurs propriétaires (Retraite, Fiscalité, Trésorerie, Simulation,
 * Hypothèses économiques). Fonction pure, sans port ni dépendance Spring : remplace
 * {@code SettingsModelAssembler}, qui relisait lui-même ces cinq ports derrière
 * {@code transition.port.SettingsReader}.
 */
public final class SettingsComposer {

    private SettingsComposer() {
    }

    public static SettingsView compose(
            RetirementSettingsModel retirement,
            TaxSettingsModel tax,
            TresorerieSettingsModel tresorerie,
            SimulationSettingsModel simulation,
            EconomicAssumptionsModel economic) {
        return new SettingsView(
                retirement != null ? retirement.birthYear() : null,
                retirement != null ? retirement.retireAge() : null,
                simulation != null ? simulation.simulateUntilAge() : null,
                economic != null ? economic.inflationRate() : null,
                tresorerie != null ? tresorerie.pivotDate() : null,
                tresorerie != null ? tresorerie.pivotMode() : null,
                tresorerie != null ? tresorerie.startBalance() : null,
                tax != null ? tax.childExitAge() : null,
                tax != null ? tax.taxAbattement() : null,
                tresorerie != null ? tresorerie.sweepEnabled() : null,
                tresorerie != null ? tresorerie.cashCeiling() : null,
                tresorerie != null ? tresorerie.cashFloor() : null,
                tresorerie != null ? tresorerie.cashAlertThreshold() : null);
    }
}
