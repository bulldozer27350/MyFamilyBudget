package com.moe.myfamilybudget.application.snapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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
import com.moe.myfamilybudget.transition.port.BudgetMutationLock;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.transition.port.EconomicAssumptionsSnapshotWriter;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.transition.port.SimulationSettingsSnapshotWriter;

/**
 * SILO-119 (lot B2) : l'import et le reset appellent le port {@code replace}/{@code reset} de chaque silo,
 * verrou pris en premier, puis les paramètres Objectifs. Test unitaire (ports mockés, sans Spring).
 */
class GlobalBudgetSnapshotFragmentsTest {

    private BudgetMutationLock lock;
    private ObjectifsSettingsService objectifs;
    private RetirementSnapshotWriter retirement;
    private TaxSnapshotWriter tax;
    private TresorerieSnapshotWriter tresorerie;
    private SimulationSettingsSnapshotWriter simulation;
    private EconomicAssumptionsSnapshotWriter economic;
    private PatrimoineSnapshotWriter patrimoine;
    private LoanSnapshotWriter loans;
    private GoalSnapshotWriter goals;
    private BankSnapshotWriter bank;
    private GlobalBudgetSnapshotService service;

    @BeforeEach
    void setUp() {
        lock = mock(BudgetMutationLock.class);
        objectifs = mock(ObjectifsSettingsService.class);
        retirement = mock(RetirementSnapshotWriter.class);
        tax = mock(TaxSnapshotWriter.class);
        tresorerie = mock(TresorerieSnapshotWriter.class);
        simulation = mock(SimulationSettingsSnapshotWriter.class);
        economic = mock(EconomicAssumptionsSnapshotWriter.class);
        patrimoine = mock(PatrimoineSnapshotWriter.class);
        loans = mock(LoanSnapshotWriter.class);
        goals = mock(GoalSnapshotWriter.class);
        bank = mock(BankSnapshotWriter.class);
        service = new GlobalBudgetSnapshotService(lock, new OverviewMapper(), objectifs,
                mock(SettingsReader.class), mock(BudgetReader.class), mock(PatrimoineReader.class),
                mock(RetirementReader.class), mock(TaxReader.class), mock(BankReader.class),
                mock(LoanReader.class), mock(GoalReader.class), retirement, tax, tresorerie, simulation,
                economic, patrimoine, loans, goals, bank);
    }

    @Test
    @DisplayName("importSnapshot : verrou, puis replace de chaque silo, puis paramètres Objectifs")
    void importReplacesEachSiloAfterTheLock() {
        service.importSnapshot(new BudgetDataDto());

        InOrder order = inOrder(lock, retirement, tax, tresorerie, simulation, economic, patrimoine, loans,
                goals, bank, objectifs);
        order.verify(lock).lockForCurrentTransaction();
        order.verify(retirement).replace(any(), any());
        order.verify(tax).replace(any(), anyList(), anyList(), anyList(), anyList());
        order.verify(tresorerie).replace(any(), anyList(), anyList(), anyList(), anyList(), anyList());
        order.verify(simulation).replace(any());
        order.verify(economic).replace(any());
        order.verify(patrimoine).replace(anyList(), anyList(), anyList(), anyList());
        order.verify(loans).replace(anyList());
        order.verify(goals).replace(anyList());
        order.verify(bank).replace(any());
        order.verify(objectifs).save(any());
    }

    @Test
    @DisplayName("importSnapshot(null) n'écrit rien")
    void importNullWritesNothing() {
        service.importSnapshot(null);

        // L'import renvoie l'etat courant (export) : les paramètres Objectifs sont donc lus, jamais écrits.
        verifyNoInteractions(lock, retirement, tax, tresorerie, simulation, economic, patrimoine, loans, goals,
                bank);
        verify(objectifs, never()).save(any());
        verify(objectifs, never()).reset();
    }

    @Test
    @DisplayName("reset : verrou, puis reset de chaque silo, puis paramètres Objectifs")
    void resetResetsEachSiloAfterTheLock() {
        service.reset();

        InOrder order = inOrder(lock, retirement, tax, tresorerie, simulation, economic, patrimoine, loans,
                goals, bank, objectifs);
        order.verify(lock).lockForCurrentTransaction();
        order.verify(retirement).reset();
        order.verify(tax).reset();
        order.verify(tresorerie).reset();
        order.verify(simulation).reset();
        order.verify(economic).reset();
        order.verify(patrimoine).reset();
        order.verify(loans).reset();
        order.verify(goals).reset();
        order.verify(bank).reset();
        order.verify(objectifs).reset();
        verify(lock).lockForCurrentTransaction();
    }
}
