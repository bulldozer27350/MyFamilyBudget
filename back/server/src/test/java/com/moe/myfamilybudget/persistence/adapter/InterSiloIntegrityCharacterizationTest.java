package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.GoalCommandService;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryGoalStore;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * SILO-240 (lot A) -- tests de caracterisation des regles d'integrite entre silos, telles qu'elles sont
 * aujourd'hui garanties par le modele global en memoire et les mutations generiques.
 *
 * <p>Ces tests figent le comportement ACTUEL (y compris ses trous) afin que le portage de ces regles dans
 * {@code application} (lot B) soit verifiable : une regle deplacee doit rester verte, un trou comble
 * doit modifier explicitement le test concerne. Inventaire complet : section SILO-240 de
 * {@code 21-plan-silotage.md}.
 *
 * <p>SILO-240 (lot B1) : la regle de sur-allocation Objectifs -> Patrimoine est portee dans
 * {@link GoalCommandService} ; ses tests passent maintenant par ce service (memes scenarios, memes messages),
 * les trois trous restent figes sur les adaptateurs.
 */
class InterSiloIntegrityCharacterizationTest {

    private PatrimoinePersistenceAdapter patrimoine;
    private InMemoryGoalStore goals;
    private GoalCommandService goalCommands;

    @BeforeEach
    void setUp() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        // R-20 / DA-14 : les virements vivent dans le silo Tresorerie ; Patrimoine ne les lit ni ne les ecrit sans lui.
        patrimoine = new PatrimoinePersistenceAdapter(persistenceManager, null, null, null,
                new com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore().getDelegate());
        goals = new InMemoryGoalStore();
        goalCommands = new GoalCommandService(goals, goals, patrimoine, silos -> { },
                RecordingTransactionRunner.direct());
    }

    private void savePlacement(String id, String label, String balance) {
        patrimoine.savePatrimoineRow(PatrimoineList.PLACEMENTS,
                Map.of("id", id, "label", label, "balance", new BigDecimal(balance)));
    }

    private Map<String, Object> goalBody(String id, String... placementAndAmount) {
        List<Map<String, Object>> allocations = new java.util.ArrayList<>();
        for (int i = 0; i < placementAndAmount.length; i += 2) {
            allocations.add(Map.of("placementId", placementAndAmount[i], "amount", placementAndAmount[i + 1]));
        }
        return Map.of("id", id, "label", "Objectif " + id, "targetAmount", new BigDecimal("5000"),
                "targetDate", "2027-06-01", "allocations", allocations);
    }

    @Test
    @DisplayName("Objectifs -> Patrimoine : une allocation superieure au solde du compte est refusee")
    void allocationAboveBalanceIsRejected() {
        savePlacement("plc_1", "Livret A", "1000");

        assertThatThrownBy(() -> goalCommands.saveGoalRow(goalBody("goal_1", "plc_1", "1500")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Livret A")
                .hasMessageContaining("solde suffisant");
        assertThat(goals.getGoals()).isEmpty();
    }

    @Test
    @DisplayName("Objectifs -> Patrimoine : le solde disponible tient compte des allocations des AUTRES objectifs")
    void availableBalanceDeductsAllocationsOfOtherGoals() {
        savePlacement("plc_1", "Livret A", "1000");
        goalCommands.saveGoalRow(goalBody("goal_1", "plc_1", "600"));

        assertThatThrownBy(() -> goalCommands.saveGoalRow(goalBody("goal_2", "plc_1", "500")))
                .isInstanceOf(IllegalArgumentException.class);
        goalCommands.saveGoalRow(goalBody("goal_2", "plc_1", "400"));

        assertThat(goals.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1", "goal_2");
    }

    @Test
    @DisplayName("Objectifs -> Patrimoine : rééditer un objectif remplace ses propres allocations")
    void editingAGoalReplacesItsOwnAllocations() {
        savePlacement("plc_1", "Livret A", "1000");
        goalCommands.saveGoalRow(goalBody("goal_1", "plc_1", "900"));

        goalCommands.saveGoalRow(goalBody("goal_1", "plc_1", "1000"));

        assertThat(goals.getGoals()).hasSize(1);
        assertThat(goals.getGoals().get(0).getEffectiveAllocations())
                .extracting(ObjectifAllocationModel::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("TROU : supprimer un placement alloue a un objectif ne touche pas l'objectif (allocation orpheline)")
    void deletingAllocatedPlacementLeavesOrphanAllocation() {
        savePlacement("plc_1", "Livret A", "1000");
        goalCommands.saveGoalRow(goalBody("goal_1", "plc_1", "600"));

        patrimoine.deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");

        assertThat(patrimoine.getPlacements()).extracting(PlacementModel::id).doesNotContain("plc_1");
        assertThat(goals.getGoals()).hasSize(1);
        assertThat(goals.getGoals().get(0).getEffectiveAllocations())
                .extracting(ObjectifAllocationModel::placementId)
                .containsExactly("plc_1");
    }

    @Test
    @DisplayName("TROU : supprimer une categorie d'actif ne modifie pas le categoryId des placements")
    void removingAssetCategoryKeepsDanglingCategoryIdOnPlacements() {
        patrimoine.addAssetCategory(new AssetCategoryModel("cat_1", "💧", "Liquide", "liquid"));
        patrimoine.savePatrimoineRow(PatrimoineList.PLACEMENTS,
                Map.of("id", "plc_1", "label", "Livret A", "balance", new BigDecimal("100"), "categoryId", "cat_1"));

        patrimoine.removeAssetCategory("cat_1");

        assertThat(patrimoine.getAssetCategories()).isEmpty();
        assertThat(patrimoine.getPlacements()).extracting(PlacementModel::categoryId).containsExactly("cat_1");
    }

    @Test
    @DisplayName("TROU : supprimer un placement ne touche pas les virements qui le designent")
    void deletingPlacementKeepsTransfersPointingAtIt() {
        savePlacement("plc_1", "Livret A", "1000");
        patrimoine.savePatrimoineRow(PatrimoineList.TRANSFERS,
                Map.of("id", "trf_1", "placement", "plc_1", "date", "2026-05-01", "amount", new BigDecimal("50")));

        patrimoine.deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");

        assertThat(patrimoine.getTransfers()).hasSize(1);
        assertThat(patrimoine.getTransfers().get(0).placement()).isEqualTo("plc_1");
    }
}
