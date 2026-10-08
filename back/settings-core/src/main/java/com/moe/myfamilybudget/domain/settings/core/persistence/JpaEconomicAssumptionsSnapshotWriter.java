package com.moe.myfamilybudget.domain.settings.core.persistence;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsSnapshotWriter;

/**
 * Port d'import et de reinitialisation des hypotheses economiques (R-50), branche sur {@link JpaAppSettingsStore}.
 * Classe distincte de {@link JpaSimulationSettingsSnapshotWriter} : les deux ports portent chacun un
 * {@code reset()} qui ne doit pas etre fusionne. Rejoint la transaction ouverte par l'appelant, apres la prise du
 * verrou du silo Parametres.
 */
@Component
public class JpaEconomicAssumptionsSnapshotWriter implements EconomicAssumptionsSnapshotWriter {

    private final JpaAppSettingsStore store;

    public JpaEconomicAssumptionsSnapshotWriter(JpaAppSettingsStore store) {
        this.store = store;
    }

    @Override
    public void replace(EconomicAssumptionsModel assumptions) {
        store.replaceEconomicAssumptions(assumptions);
    }

    @Override
    public void reset() {
        store.resetEconomicAssumptions();
    }
}
