package com.moe.myfamilybudget.server.internal.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.GoalAllocationRule;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * SILO-240 (lot B1) -- la regle de sur-allocation Objectifs -> Patrimoine, isolee de toute persistance : memes
 * resultats que l'ancienne validation des mutations generiques.
 */
@DisplayName("SILO-240 -- GoalAllocationRule")
class GoalAllocationRuleTest {

    private static PlacementModel placement(String id, String label, String balance) {
        return new PlacementModel(id, label, "Epargne", new BigDecimal(balance), "2026-01-01", BigDecimal.ZERO,
                "2026-01-01", "2060-12-31", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, "");
    }

    private static ObjectifAllocationModel allocation(String placementId, String amount) {
        return new ObjectifAllocationModel("alloc_" + placementId, placementId, new BigDecimal(amount));
    }

    private static ObjectifModel goal(String id, ObjectifAllocationModel... allocations) {
        return new ObjectifModel(id, "Objectif " + id, new BigDecimal("5000"), null, "2027-06-01", "", "",
                List.of(allocations));
    }

    @Test
    @DisplayName("une allocation egale au solde disponible est acceptee")
    void allocationEqualToBalanceIsAccepted() {
        assertThatCode(() -> GoalAllocationRule.validate("goal_1", List.of(allocation("plc_1", "1000")),
                List.of(placement("plc_1", "Livret A", "1000")), List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("une allocation superieure au solde est refusee avec le message historique")
    void allocationAboveBalanceIsRejected() {
        assertThatThrownBy(() -> GoalAllocationRule.validate("goal_1", List.of(allocation("plc_1", "1500")),
                List.of(placement("plc_1", "Livret A", "1000")), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le compte 'Livret A' n'a pas un solde suffisant pour cette allocation : "
                        + "disponible 1000 €, montant demandé 1500 €.");
    }

    @Test
    @DisplayName("les allocations de l'objectif sauvegarde ne comptent pas dans le deja alloue ailleurs")
    void ownAllocationsAreReplaced() {
        assertThatCode(() -> GoalAllocationRule.validate("goal_1", List.of(allocation("plc_1", "1000")),
                List.of(placement("plc_1", "Livret A", "1000")),
                List.of(goal("goal_1", allocation("plc_1", "900"))))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("les allocations des autres objectifs sont deduites du solde")
    void otherGoalsAreDeducted() {
        List<PlacementModel> placements = List.of(placement("plc_1", "Livret A", "1000"));
        List<ObjectifModel> goals = List.of(goal("goal_1", allocation("plc_1", "600")));

        assertThatThrownBy(() -> GoalAllocationRule.validate("goal_2", List.of(allocation("plc_1", "500")),
                placements, goals))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disponible 400 €");
        assertThatCode(() -> GoalAllocationRule.validate("goal_2", List.of(allocation("plc_1", "400")),
                placements, goals)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("plusieurs lignes sur le meme compte sont additionnees")
    void sameAccountLinesAreSummed() {
        assertThatThrownBy(() -> GoalAllocationRule.validate("goal_1",
                List.of(allocation("plc_1", "600"), allocation("plc_1", "600")),
                List.of(placement("plc_1", "Livret A", "1000")), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un compte inconnu a un solde nul : tout montant positif est refuse, un montant nul passe")
    void unknownAccountHasZeroBalance() {
        assertThatThrownBy(() -> GoalAllocationRule.validate("goal_1", List.of(allocation("plc_x", "1")),
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'plc_x'");
        assertThatCode(() -> GoalAllocationRule.validate("goal_1", List.of(allocation("plc_x", "0")),
                List.of(), List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un objectif au format mono-compte (sans allocation) compte pour son compte source")
    void legacySingleAccountGoalIsCounted() {
        ObjectifModel legacy = new ObjectifModel("goal_old", "Ancien", new BigDecimal("5000"),
                new BigDecimal("700"), "2027-06-01", "plc_1", "");

        assertThatThrownBy(() -> GoalAllocationRule.validate("goal_2", List.of(allocation("plc_1", "400")),
                List.of(placement("plc_1", "Livret A", "1000")), List.of(legacy)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disponible 300 €");
    }

    @Test
    @DisplayName("sans allocation demandee, rien n'est controle")
    void noRequestedAllocationMeansNoCheck() {
        assertThatCode(() -> GoalAllocationRule.validate("goal_1", List.of(), List.of(), List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> GoalAllocationRule.validate("goal_1", null, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("lecture du corps : compte absent -> chaine vide, montant absent ou illisible -> zero, virgule decimale acceptee")
    void allocationsAreReadLikeTheWritePath() {
        Map<String, Object> noPlacement = new HashMap<>();
        noPlacement.put("amount", "12,5");
        Map<String, Object> garbage = Map.of("placementId", "plc_2", "amount", "abc");
        Map<String, Object> numeric = Map.of("id", "a3", "placementId", "plc_3", "amount", 7);
        Map<String, Object> body = Map.of("allocations", List.of(noPlacement, garbage, "ignore", numeric));

        List<ObjectifAllocationModel> allocations = GoalAllocationRule.allocationsOf(body);

        assertThat(allocations).extracting(ObjectifAllocationModel::placementId).containsExactly("", "plc_2", "plc_3");
        assertThat(allocations).extracting(ObjectifAllocationModel::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("12.5"), BigDecimal.ZERO, new BigDecimal("7"));
        assertThat(GoalAllocationRule.allocationsOf(null)).isEmpty();
        assertThat(GoalAllocationRule.allocationsOf(Map.of("allocations", "pas une liste"))).isEmpty();
    }

    @Test
    @DisplayName("identifiant de l'objectif : absent ou vide -> null (nouvel objectif)")
    void goalIdIsNullWhenBlank() {
        Map<String, Object> blank = new HashMap<>();
        blank.put("id", "  ");
        assertThat(GoalAllocationRule.goalIdOf(Map.of("id", "goal_1"))).isEqualTo("goal_1");
        assertThat(GoalAllocationRule.goalIdOf(blank)).isNull();
        assertThat(GoalAllocationRule.goalIdOf(Map.of())).isNull();
        assertThat(GoalAllocationRule.goalIdOf(null)).isNull();
    }
}
