package com.moe.myfamilybudget.application.command;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsWriter;

/**
 * Owner applicatif de la notion Simulation (SET-020, voir doc/architecture/12-settings.md) : profondeur de
 * simulation ({@code simulateUntilAge}). Ce n'est pas un domaine Settings global : une seule propriété.
 */
@Service
public class SimulationSettingsCommandService {

    private final SimulationSettingsWriter writer;

    public SimulationSettingsCommandService(SimulationSettingsWriter writer) {
        this.writer = writer;
    }

    public void updateSimulateUntilAge(Object value) {
        writer.updateSimulateUntilAge(value);
    }
}
