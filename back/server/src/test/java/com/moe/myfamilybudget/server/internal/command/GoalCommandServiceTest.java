package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.GoalCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryGoalStore;
import com.moe.myfamilybudget.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * DB-041 -- Le command service Objectifs valide la commande, delegue au port d'ecriture et laisse l'erreur de
 * persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le lecteur.
 *
 * <p>SILO-240 (lot B1) : la regle de sur-allocation Objectifs -> Patrimoine s'execute ici, dans la transaction et
 * apres la prise des verrous des silos Patrimoine et Objectifs.
 */
@DisplayName("DB-041 -- GoalCommandService")
class GoalCommandServiceTest {

    private GoalWriter writer;
    private GoalReader goalReader;
    private PatrimoineReader patrimoineReader;
    private SiloMutationLock lock;
    private RecordingTransactionRunner transactions;
    private GoalCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(GoalWriter.class);
        goalReader = mock(GoalReader.class);
        patrimoineReader = mock(PatrimoineReader.class);
        lock = mock(SiloMutationLock.class);
        transactions = RecordingTransactionRunner.direct();
        service = new GoalCommandService(writer, goalReader, patrimoineReader, lock, transactions);
    }

    @Test
    @DisplayName("succes : sauvegarde et suppression sont transmises au port, le resultat est renvoye")
    void commandsDelegate() {
        Map<String, Object> body = Map.of("id", "goal_1", "label", "Test");
        Map<String, Object> saved = Map.of("id", "goal_1");
        when(writer.saveGoalRow(body)).thenReturn(saved);

        assertThat(service.saveGoalRow(body)).isSameAs(saved);
        service.deleteGoalRow("goal_1");

        verify(writer).deleteGoalRow("goal_1");
    }

    @Test
    @DisplayName("succes : un corps null reste accepte (creation par defaut, contrat historique)")
    void nullBodyIsAllowed() {
        service.saveGoalRow(null);

        verify(writer).saveGoalRow(null);
    }

    @Test
    @DisplayName("SILO-212 : la suppression s'execute dans la transaction, apres la prise du verrou du silo Objectifs")
    void deleteRunsInTransactionAfterLockingGoalsSilo() {
        service.deleteGoalRow("goal_1");

        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.GOALS));
        order.verify(writer).deleteGoalRow("goal_1");
    }

    @Test
    @DisplayName("validation : un identifiant null est refuse et rien n'est ecrit")
    void nullIdIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.deleteGoalRow(null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailuresArePropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        when(writer.saveGoalRow(any())).thenThrow(dbDown);
        doThrow(dbDown).when(writer).deleteGoalRow(anyString());

        assertThatThrownBy(() -> service.saveGoalRow(Map.of())).isSameAs(dbDown);
        assertThatThrownBy(() -> service.deleteGoalRow("goal_1")).isSameAs(dbDown);
    }

    @Test
    @DisplayName("integration adaptateur : objectif ecrit puis supprime, relu par le lecteur")
    void adapterWritesAreReadBackByReader() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        InMemoryGoalStore adapter = new InMemoryGoalStore();
        GoalCommandService realService = new GoalCommandService(adapter, adapter,
                new PatrimoinePersistenceAdapter(persistenceManager), silos -> { }, RecordingTransactionRunner.direct());

        realService.saveGoalRow(Map.of("id", "goal_1", "label", "Vacances", "targetAmount", new BigDecimal("3000"), "targetDate", "2027-07-01"));

        assertThat(adapter.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1");

        realService.deleteGoalRow("goal_1");

        assertThat(adapter.getGoals()).isEmpty();
    }

    private static PlacementModel placement(String id, String label, String balance) {
        return new PlacementModel(id, label, "Epargne", new BigDecimal(balance), "2026-01-01", BigDecimal.ZERO,
                "2026-01-01", "2060-12-31", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, "");
    }

    private static Map<String, Object> bodyWithAllocation(String goalId, String placementId, String amount) {
        return Map.of("id", goalId, "label", "Objectif", "allocations",
                List.of(Map.of("placementId", placementId, "amount", amount)));
    }

    @Test
    @DisplayName("SILO-240 : la sauvegarde s'execute dans une transaction, silos Patrimoine et Objectifs verrouilles avant l'ecriture")
    void saveRunsInTransactionAfterLockingSilos() {
        Map<String, Object> body = Map.of("id", "goal_1", "label", "Test");

        service.saveGoalRow(body);

        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.WEALTH, MutationSilo.GOALS));
        order.verify(writer).saveGoalRow(body);
    }

    @Test
    @DisplayName("SILO-240 : sans allocation, aucune lecture de controle n'est faite")
    void noAllocationMeansNoValidationReads() {
        service.saveGoalRow(Map.of("id", "goal_1", "label", "Test"));

        verifyNoInteractions(goalReader, patrimoineReader);
    }

    @Test
    @DisplayName("SILO-240 : une allocation superieure au solde est refusee et rien n'est ecrit")
    void overAllocationIsRejectedWithoutWriting() {
        when(patrimoineReader.getPlacements()).thenReturn(List.of(placement("plc_1", "Livret A", "1000")));
        when(goalReader.getGoals()).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveGoalRow(bodyWithAllocation("goal_1", "plc_1", "1500")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le compte 'Livret A' n'a pas un solde suffisant pour cette allocation : "
                        + "disponible 1000 €, montant demandé 1500 €.");

        verify(writer, never()).saveGoalRow(any());
    }

    @Test
    @DisplayName("SILO-240 : le solde disponible tient compte des allocations des autres objectifs")
    void otherGoalsAllocationsAreDeducted() {
        when(patrimoineReader.getPlacements()).thenReturn(List.of(placement("plc_1", "Livret A", "1000")));
        when(goalReader.getGoals()).thenReturn(List.of(new ObjectifModel("goal_1", "Autre", new BigDecimal("5000"),
                null, "2027-06-01", "", "", List.of(new ObjectifAllocationModel("a1", "plc_1", new BigDecimal("600"))))));

        assertThatThrownBy(() -> service.saveGoalRow(bodyWithAllocation("goal_2", "plc_1", "500")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(writer, never()).saveGoalRow(any());

        service.saveGoalRow(bodyWithAllocation("goal_2", "plc_1", "400"));

        verify(writer).saveGoalRow(any());
    }
}
