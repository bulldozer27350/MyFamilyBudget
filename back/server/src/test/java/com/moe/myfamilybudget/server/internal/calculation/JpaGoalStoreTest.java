package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.goals.core.persistence.JpaGoalStore;
import com.moe.myfamilybudget.domain.goals.model.GoalsMutatedEvent;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryGoalRepository;

/**
 * SILO-212 (lot B1) -- Adaptateur du silo Objectifs : parite avec l'ancienne mutation generique
 * ({@code savePatrimoineRow("objectifs", ...)}) et publication d'un evenement apres chaque ecriture. Le
 * repository est en memoire ; le round-trip contre une vraie base est couvert par
 * {@code PersistenceAdaptersJpaRoundTripTest}.
 */
@DisplayName("SILO-212 -- JpaGoalStore")
class JpaGoalStoreTest {

    private final List<Object> events = new ArrayList<>();
    private JpaGoalStore store;

    @BeforeEach
    void setUp() {
        store = new JpaGoalStore(InMemoryGoalRepository.create(), events::add);
    }

    @Test
    @DisplayName("creation : valeurs par defaut, identifiant genere et ligne renvoyee comme l'ancienne mutation")
    void createsWithDefaultsAndGeneratedId() {
        Map<String, Object> row = store.saveGoalRow(null);

        String id = String.valueOf(row.get("id"));
        assertThat(id).startsWith("pat_").hasSize(12);
        assertThat(row.get("label")).isEqualTo("Nouvel objectif");
        assertThat((BigDecimal) row.get("targetAmount")).isEqualByComparingTo("0");
        assertThat(row.get("targetDate")).isEqualTo("2027-01-01");
        assertThat(row.get("notes")).isEqualTo("");
        assertThat((List<?>) row.get("allocations")).isEmpty();
        assertThat(store.getGoals()).singleElement().satisfies(goal -> {
            assertThat(goal.id()).isEqualTo(id);
            assertThat(goal.allocatedAmount()).isNull();
            assertThat(goal.sourcePlacementId()).isEmpty();
        });
    }

    @Test
    @DisplayName("un identifiant vide est traite comme absent")
    void blankIdIsGenerated() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "   ");

        assertThat(String.valueOf(store.saveGoalRow(body).get("id"))).startsWith("pat_");
    }

    @Test
    @DisplayName("les montants sont lus comme avant : nombre, texte avec virgule, valeur illisible = defaut")
    void readsAmountsLeniently() {
        store.saveGoalRow(Map.of("id", "g_number", "targetAmount", 1200.5));
        store.saveGoalRow(Map.of("id", "g_text", "targetAmount", "3000,25"));
        store.saveGoalRow(Map.of("id", "g_bad", "targetAmount", "abc"));

        assertThat(amountOf("g_number")).isEqualByComparingTo("1200.5");
        assertThat(amountOf("g_text")).isEqualByComparingTo("3000.25");
        assertThat(amountOf("g_bad")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("allocations : identifiant genere si absent, lignes non-objet ignorees, montant par defaut 0")
    void readsAllocations() {
        List<Object> allocations = new ArrayList<>();
        allocations.add(Map.of("placementId", "plc_1", "amount", "250"));
        allocations.add("n'est pas une allocation");
        allocations.add(Map.of("id", "al_fixed", "placementId", "plc_2"));

        store.saveGoalRow(Map.of("id", "goal_1", "allocations", allocations));

        List<ObjectifAllocationModel> read = store.getGoals().get(0).getEffectiveAllocations();
        assertThat(read).hasSize(2);
        assertThat(read.get(0).id()).isNotBlank();
        assertThat(read.get(0).placementId()).isEqualTo("plc_1");
        assertThat(read.get(0).amount()).isEqualByComparingTo("250");
        assertThat(read.get(1).id()).isEqualTo("al_fixed");
        assertThat(read.get(1).amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("reedition : l'objectif garde sa place, un nouvel objectif est ajoute a la fin")
    void editingKeepsPositionAndNewGoalsAreAppended() {
        store.saveGoalRow(Map.of("id", "goal_1", "label", "A"));
        store.saveGoalRow(Map.of("id", "goal_2", "label", "B"));

        store.saveGoalRow(Map.of("id", "goal_1", "label", "A bis"));
        store.saveGoalRow(Map.of("id", "goal_3", "label", "C"));

        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1", "goal_2", "goal_3");
        assertThat(store.getGoals().get(0).label()).isEqualTo("A bis");
    }

    @Test
    @DisplayName("suppression : seul l'objectif vise disparait ; un identifiant inconnu ne change rien")
    void deleteRemovesOnlyTheTarget() {
        store.saveGoalRow(Map.of("id", "goal_1"));
        store.saveGoalRow(Map.of("id", "goal_2"));

        store.deleteGoalRow("unknown");
        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1", "goal_2");

        store.deleteGoalRow("goal_1");
        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_2");
    }

    @Test
    @DisplayName("replace : remplace tout, ignore les objectifs sans identifiant, null = vide ; reset vide")
    void replaceAndReset() {
        store.saveGoalRow(Map.of("id", "old"));

        store.replace(List.of(
                new ObjectifModel("goal_1", "A", new BigDecimal("10"), "2027-01-01", "", ""),
                new ObjectifModel(null, "sans id", new BigDecimal("10"), "2027-01-01", "", "")));
        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1");

        store.replace(null);
        assertThat(store.getGoals()).isEmpty();

        store.replace(List.of(new ObjectifModel("goal_2", "B", new BigDecimal("10"), "2027-01-01", "", "")));
        store.reset();
        assertThat(store.getGoals()).isEmpty();
    }

    @Test
    @DisplayName("chaque ecriture publie un evenement de mutation, une lecture n'en publie pas")
    void publishesOneEventPerWrite() {
        store.getGoals();
        assertThat(events).isEmpty();

        store.saveGoalRow(Map.of("id", "goal_1"));
        store.deleteGoalRow("goal_1");
        store.replace(List.of());
        store.reset();

        assertThat(events).containsExactly(
                new GoalsMutatedEvent("saveGoalRow"),
                new GoalsMutatedEvent("deleteGoalRow"),
                new GoalsMutatedEvent("replace"),
                new GoalsMutatedEvent("reset"));
    }

    private BigDecimal amountOf(String id) {
        return store.getGoals().stream()
                .filter(goal -> id.equals(goal.id()))
                .findFirst()
                .orElseThrow()
                .targetAmount();
    }
}
