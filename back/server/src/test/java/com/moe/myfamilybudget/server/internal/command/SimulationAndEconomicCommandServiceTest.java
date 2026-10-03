package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.EconomicAssumptionsCommandService;
import com.moe.myfamilybudget.application.command.SimulationSettingsCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.transition.port.SimulationSettingsWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/** SET-020 : owners minimaux Simulation ({@code simulateUntilAge}) et Hypothèses économiques ({@code inflationRate}). */
@DisplayName("SET-020 -- SimulationSettingsCommandService / EconomicAssumptionsCommandService")
class SimulationAndEconomicCommandServiceTest {

    @Test
    @DisplayName("simulateUntilAge est transmis une fois au port d'ecriture Simulation")
    void simulationDelegatesToWriter() {
        SimulationSettingsWriter writer = mock(SimulationSettingsWriter.class);

        new SimulationSettingsCommandService(writer).updateSimulateUntilAge(90);

        verify(writer).updateSimulateUntilAge(90);
    }

    @Test
    @DisplayName("inflationRate est transmis une fois au port d'ecriture Hypotheses economiques")
    void economicAssumptionsDelegatesToWriter() {
        EconomicAssumptionsWriter writer = mock(EconomicAssumptionsWriter.class);

        new EconomicAssumptionsCommandService(writer).updateInflationRate(new BigDecimal("0.03"));

        verify(writer).updateInflationRate(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("integration adaptateur : les deux ecritures sont relues par SettingsReader")
    void adapterWritesAreReadBackBySettingsReader() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        SettingsPersistenceAdapter adapter = new SettingsPersistenceAdapter(persistenceManager);

        new SimulationSettingsCommandService(adapter).updateSimulateUntilAge(90);
        new EconomicAssumptionsCommandService(adapter).updateInflationRate(new BigDecimal("0.03"));

        assertThat(adapter.getSettings().simulateUntilAge()).isEqualTo(90);
        assertThat(adapter.getSettings().inflationRate()).isEqualByComparingTo("0.03");
    }
}
