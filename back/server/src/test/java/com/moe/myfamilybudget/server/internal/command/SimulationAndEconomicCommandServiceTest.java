package com.moe.myfamilybudget.server.internal.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.EnumSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.application.command.EconomicAssumptionsCommandService;
import com.moe.myfamilybudget.application.command.SimulationSettingsCommandService;
import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsWriter;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * SET-020 : owners minimaux Simulation ({@code simulateUntilAge}) et Hypothèses économiques ({@code inflationRate}).
 *
 * <p>R-50 : chaque écriture s'exécute dans une transaction, après la prise du verrou du silo Paramètres, partagé
 * par les deux paramètres (même ligne de {@code app_settings}).
 */
@DisplayName("SET-020 -- SimulationSettingsCommandService / EconomicAssumptionsCommandService")
class SimulationAndEconomicCommandServiceTest {

    private SiloMutationLock lock;
    private RecordingTransactionRunner transactions;

    @BeforeEach
    void setUp() {
        lock = mock(SiloMutationLock.class);
        transactions = RecordingTransactionRunner.direct();
    }

    @Test
    @DisplayName("simulateUntilAge est transmis une fois au port d'ecriture, apres le verrou du silo Parametres")
    void simulationDelegatesToWriterAfterLocking() {
        SimulationSettingsWriter writer = mock(SimulationSettingsWriter.class);

        new SimulationSettingsCommandService(writer, lock, transactions).updateSimulateUntilAge(90);

        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.SETTINGS));
        order.verify(writer).updateSimulateUntilAge(90);
        assertThat(transactions.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("inflationRate est transmis une fois au port d'ecriture, apres le verrou du silo Parametres")
    void economicAssumptionsDelegatesToWriterAfterLocking() {
        EconomicAssumptionsWriter writer = mock(EconomicAssumptionsWriter.class);

        new EconomicAssumptionsCommandService(writer, lock, transactions).updateInflationRate(new BigDecimal("0.03"));

        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.SETTINGS));
        order.verify(writer).updateInflationRate(new BigDecimal("0.03"));
        assertThat(transactions.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("l'ecriture s'execute dans la transaction et une erreur de persistance remonte telle quelle")
    void writesRunInTransactionAndErrorsPropagate() {
        RuntimeException failure = new IllegalStateException("base indisponible");
        SimulationSettingsWriter simulationWriter = mock(SimulationSettingsWriter.class);
        EconomicAssumptionsWriter economicWriter = mock(EconomicAssumptionsWriter.class);
        doThrow(failure).when(simulationWriter).updateSimulateUntilAge(90);
        doThrow(failure).when(economicWriter).updateInflationRate(new BigDecimal("0.03"));

        assertThatThrownBy(() -> new SimulationSettingsCommandService(simulationWriter, lock, transactions)
                .updateSimulateUntilAge(90)).isSameAs(failure);
        assertThatThrownBy(() -> new EconomicAssumptionsCommandService(economicWriter, lock, transactions)
                .updateInflationRate(new BigDecimal("0.03"))).isSameAs(failure);

        verify(lock, org.mockito.Mockito.times(2)).lockForCurrentTransaction(EnumSet.of(MutationSilo.SETTINGS));
        assertThat(transactions.calls()).isEqualTo(2);
    }
}
