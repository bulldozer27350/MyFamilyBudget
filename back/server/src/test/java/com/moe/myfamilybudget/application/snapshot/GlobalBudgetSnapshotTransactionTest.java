package com.moe.myfamilybudget.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.application.mapper.OverviewMapper;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.bankpointage.port.BankSnapshotWriter;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.credit.port.LoanSnapshotWriter;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalSnapshotWriter;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSnapshotWriter;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSnapshotWriter;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSnapshotWriter;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineSnapshotWriter;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;
import com.moe.myfamilybudget.transition.port.BudgetMutationLock;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsSnapshotWriter;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.transition.port.SimulationSettingsSnapshotWriter;

/** SILO-205 : import et reset s'exécutent dans la transaction ouverte par le port, verrou compris. */
@DisplayName("SILO-205 -- GlobalBudgetSnapshotService via TransactionRunner")
class GlobalBudgetSnapshotTransactionTest {

    private RecordingTransactionRunner runner;
    private List<Boolean> lockCalledInTransaction;
    private List<Boolean> writerCalledInTransaction;
    private GlobalBudgetSnapshotService service;

    @BeforeEach
    void setUp() {
        runner = RecordingTransactionRunner.direct();
        lockCalledInTransaction = new ArrayList<>();
        writerCalledInTransaction = new ArrayList<>();

        BudgetMutationLock lock = mock(BudgetMutationLock.class);
        doAnswer(invocation -> {
            lockCalledInTransaction.add(runner.isActive());
            return null;
        }).when(lock).lockForCurrentTransaction();
        BankSnapshotWriter bank = mock(BankSnapshotWriter.class);
        doAnswer(invocation -> {
            writerCalledInTransaction.add(runner.isActive());
            return null;
        }).when(bank).reset();
        doAnswer(invocation -> {
            writerCalledInTransaction.add(runner.isActive());
            return null;
        }).when(bank).replace(any());

        service = new GlobalBudgetSnapshotService(lock, new OverviewMapper(), mock(ObjectifsSettingsService.class),
                mock(SettingsReader.class), mock(BudgetReader.class), mock(PatrimoineReader.class),
                mock(RetirementReader.class), mock(TaxReader.class), mock(BankReader.class),
                mock(LoanReader.class), mock(GoalReader.class), mock(RetirementSnapshotWriter.class),
                mock(TaxSnapshotWriter.class), mock(TresorerieSnapshotWriter.class),
                mock(SimulationSettingsSnapshotWriter.class), mock(EconomicAssumptionsSnapshotWriter.class),
                mock(PatrimoineSnapshotWriter.class), mock(LoanSnapshotWriter.class), mock(GoalSnapshotWriter.class),
                bank, runner);
    }

    @Test
    @DisplayName("importSnapshot : verrou et écritures dans la transaction")
    void importRunsInsideTheTransaction() {
        service.importSnapshot(new BudgetDataDto());

        assertThat(runner.calls()).isEqualTo(1);
        assertThat(lockCalledInTransaction).containsExactly(true);
        assertThat(writerCalledInTransaction).containsExactly(true);
    }

    @Test
    @DisplayName("importSnapshot avec un corps null : ouvre la transaction, n'écrit rien")
    void importWithNullBodyWritesNothing() {
        service.importSnapshot(null);

        assertThat(runner.calls()).isEqualTo(1);
        assertThat(lockCalledInTransaction).isEmpty();
        assertThat(writerCalledInTransaction).isEmpty();
    }

    @Test
    @DisplayName("reset : verrou et écritures dans la transaction")
    void resetRunsInsideTheTransaction() {
        service.reset();

        assertThat(runner.calls()).isEqualTo(1);
        assertThat(lockCalledInTransaction).containsExactly(true);
        assertThat(writerCalledInTransaction).containsExactly(true);
    }
}
