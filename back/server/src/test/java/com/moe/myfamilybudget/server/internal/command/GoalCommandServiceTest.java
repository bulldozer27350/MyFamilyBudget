package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.GoalCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.adapter.GoalPersistenceAdapter;
import com.moe.myfamilybudget.domain.goals.port.GoalWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-041 -- Le command service Objectifs valide la commande, delegue au port d'ecriture et laisse l'erreur de
 * persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le lecteur.
 */
@DisplayName("DB-041 -- GoalCommandService")
class GoalCommandServiceTest {

    private GoalWriter writer;
    private GoalCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(GoalWriter.class);
        service = new GoalCommandService(writer);
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
        GoalPersistenceAdapter adapter = new GoalPersistenceAdapter(persistenceManager);
        GoalCommandService realService = new GoalCommandService(adapter);

        realService.saveGoalRow(Map.of("id", "goal_1", "label", "Vacances", "targetAmount", new BigDecimal("3000"), "targetDate", "2027-07-01"));

        assertThat(adapter.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1");

        realService.deleteGoalRow("goal_1");

        assertThat(adapter.getGoals()).isEmpty();
    }
}
