package com.moe.myfamilybudget.domain.settings.core.persistence;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsSnapshotWriter;

/**
 * Port d'import et de reinitialisation du parametre de simulation (R-50), branche sur {@link JpaAppSettingsStore}.
 * Classe distincte de {@link JpaEconomicAssumptionsSnapshotWriter} : les deux ports portent chacun un
 * {@code reset()} qui ne doit pas etre fusionne. Rejoint la transaction ouverte par l'appelant, apres la prise du
 * verrou du silo Parametres.
 */
@Component
public class JpaSimulationSettingsSnapshotWriter implements SimulationSettingsSnapshotWriter {

    private final JpaAppSettingsStore store;

    public JpaSimulationSettingsSnapshotWriter(JpaAppSettingsStore store) {
        this.store = store;
    }

    @Override
    public void replace(SimulationSettingsModel settings) {
        store.replaceSimulation(settings);
    }

    @Override
    public void reset() {
        store.resetSimulation();
    }
}
