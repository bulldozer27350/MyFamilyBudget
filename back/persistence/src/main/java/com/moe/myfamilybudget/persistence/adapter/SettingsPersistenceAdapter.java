package com.moe.myfamilybudget.persistence.adapter;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.transition.model.SettingsModel;

/**
 * Adaptateur de persistance pour les lectures de paramètres par propriétaire (RF-B00) : {@link RetirementSettingsReader},
 * {@link TaxSettingsReader} et {@link TresorerieSettingsReader}. Le stockage physique reste {@code SettingsEntity}
 * pour ces trois propriétaires ; leur séparation relève des lots B de Retraite, Fiscalité et Trésorerie (R-40,
 * R-42, R-21).
 *
 * <p>R-50 : la simulation ({@code simulateUntilAge}) et les hypothèses économiques ({@code inflationRate}) ne passent
 * plus par cet adaptateur ; le silo Paramètres les lit et les écrit directement dans {@code app_settings}
 * ({@code JpaAppSettingsStore}). Le {@code SettingsModel} global est recomposé côté application
 * ({@code SettingsModelAssembler}). L'adaptateur reste un lecteur du cache pour les trois propriétaires restants ;
 * {@link #getSettings()} ne sert plus qu'à ces projections et aux tests.
 */
@Component
public class SettingsPersistenceAdapter
        implements RetirementSettingsReader, TaxSettingsReader, TresorerieSettingsReader {

    private final PersistenceManager persistenceManager;

    public SettingsPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

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
}