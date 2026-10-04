package com.moe.myfamilybudget.transition.port;

import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;

/** Port de lecture de la notion Simulation (SILO-100), symétrique de {@link SimulationSettingsWriter}. */
public interface SimulationSettingsReader {

    SimulationSettingsModel getSimulationSettings();
}
