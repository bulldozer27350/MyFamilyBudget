package com.moe.myfamilybudget.domain.notifications.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.notifications.calculation.ObjectifReachableInput;
import com.moe.myfamilybudget.domain.notifications.calculation.ObjectifReachableInput.Allocation;
import com.moe.myfamilybudget.domain.notifications.calculation.ObjectifReachableInput.GoalCoverage;
import com.moe.myfamilybudget.domain.notifications.calculation.PlacementBalanceSnapshot;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;

/** RF-703 : la règle « objectif atteignable » est testée avec {@link ObjectifReachableInput} seulement. */
class ObjectifReachableRuleTest {

    private final ObjectifReachableRule rule = new ObjectifReachableRule();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PlacementBalanceSnapshot balance(String placementId, String amount) {
        return new PlacementBalanceSnapshot(placementId, bd(amount));
    }

    @Test
    @DisplayName("Un objectif couvert par ses allocations est signalé, avec son id pour la déduplication")
    void testCoveredGoal() {
        GoalCoverage goal = new GoalCoverage("g1", "Vacances", bd("1000"),
                List.of(new Allocation("p1", bd("600")), new Allocation("p2", bd("400"))));

        List<NotificationMessage> messages = rule.check(
                new ObjectifReachableInput(List.of(goal), List.of(balance("p1", "700"), balance("p2", "500"))));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).dedupKey()).isEqualTo("objectif-reachable:g1");
        assertThat(messages.get(0).body()).contains("Vacances").contains("1000");
    }

    @Test
    @DisplayName("La couverture d'une allocation est plafonnée au solde courant du placement")
    void testCoverageCappedByBalance() {
        GoalCoverage goal = new GoalCoverage("g1", "Voiture", bd("1000"),
                List.of(new Allocation("p1", bd("900"))));

        assertThat(rule.check(new ObjectifReachableInput(List.of(goal), List.of(balance("p1", "500"))))).isEmpty();
    }

    @Test
    @DisplayName("Un placement sans solde connu ne couvre rien")
    void testUnknownPlacementCoversNothing() {
        GoalCoverage goal = new GoalCoverage("g1", "Voiture", bd("100"),
                List.of(new Allocation("inconnu", bd("100"))));

        assertThat(rule.check(new ObjectifReachableInput(List.of(goal), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("Un objectif sans montant visé ou sans allocation n'est jamais signalé")
    void testGoalsIgnored() {
        GoalCoverage noTarget = new GoalCoverage("g1", "Sans cible", BigDecimal.ZERO,
                List.of(new Allocation("p1", bd("100"))));
        GoalCoverage noAllocation = new GoalCoverage("g2", "Sans allocation", bd("100"), List.of());

        assertThat(rule.check(new ObjectifReachableInput(List.of(noTarget, noAllocation),
                List.of(balance("p1", "100"))))).isEmpty();
    }

    @Test
    @DisplayName("Sans objectif, la règle ne signale rien ; en cas de soldes en double, le premier est retenu")
    void testEmptyAndDuplicateBalances() {
        assertThat(rule.check(new ObjectifReachableInput(List.of(), List.of()))).isEmpty();

        GoalCoverage goal = new GoalCoverage("g1", "Cible", bd("100"), List.of(new Allocation("p1", bd("100"))));
        assertThat(rule.check(new ObjectifReachableInput(List.of(goal),
                List.of(balance("p1", "50"), balance("p1", "500"))))).isEmpty();
    }
}
