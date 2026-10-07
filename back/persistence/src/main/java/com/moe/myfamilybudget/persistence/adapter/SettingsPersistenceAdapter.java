package com.moe.myfamilybudget.persistence.adapter;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsMapper;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsWriter;

/**
 * Adaptateur de persistance pour {@link SettingsReader} (RF-B00), {@link SimulationSettingsWriter} et
 * {@link EconomicAssumptionsWriter} (SET-020).
 */
@Component
public class SettingsPersistenceAdapter
        implements RetirementSettingsReader, TaxSettingsReader, TresorerieSettingsReader,
        SimulationSettingsReader, EconomicAssumptionsReader, SimulationSettingsWriter, EconomicAssumptionsWriter {

    private final PersistenceManager persistenceManager;
    private final CashflowSettingsRepository cashflowSettingsRepository;

    public SettingsPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null);
    }

    @Autowired
    public SettingsPersistenceAdapter(PersistenceManager persistenceManager,
                                      @Autowired(required = false) CashflowSettingsRepository cashflowSettingsRepository) {
        this.persistenceManager = persistenceManager;
        this.cashflowSettingsRepository = cashflowSettingsRepository;
    }

    public SettingsModel getSettings() {
        SettingsModel s = persistenceManager.getBudgetData().getEffectiveSettings();
        if (cashflowSettingsRepository != null) {
            Optional<CashflowSettingsEntity> opt = cashflowSettingsRepository.findFirstByOrderByIdAsc();
            if (opt.isPresent()) {
                CashflowSettingsEntity t = opt.get();
                return new SettingsModel(
                        s.birthYear(),
                        s.retireAge(),
                        s.simulateUntilAge(),
                        s.inflationRate(),
                        t.getPivotDate() != null ? t.getPivotDate() : s.pivotDate(),
                        t.getPivotMode() != null ? t.getPivotMode() : s.pivotMode(),
                        t.getStartBalance() != null ? t.getStartBalance() : s.startBalance(),
                        s.childExitAge(),
                        s.taxAbattement(),
                        t.getSweepEnabled() != null ? t.getSweepEnabled() : s.sweepEnabled(),
                        t.getCashCeiling() != null ? t.getCashCeiling() : s.cashCeiling(),
                        t.getCashFloor() != null ? t.getCashFloor() : s.cashFloor(),
                        t.getCashAlertThreshold() != null ? t.getCashAlertThreshold() : s.cashAlertThreshold()
                );
            }
        }
        return s;
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
        if (cashflowSettingsRepository != null) {
            Optional<CashflowSettingsEntity> opt = cashflowSettingsRepository.findFirstByOrderByIdAsc();
            if (opt.isPresent()) {
                return CashflowSettingsMapper.toModel(opt.get());
            }
        }
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