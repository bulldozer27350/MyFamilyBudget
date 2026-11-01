package com.moe.myfamilybudget.domain.settings.port;

import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;

/** Port de lecture de la notion Simulation (SILO-100), symétrique de {@link SimulationSettingsWriter}. */
public interface SimulationSettingsReader {

    SimulationSettingsModel getSimulationSettings();
}
