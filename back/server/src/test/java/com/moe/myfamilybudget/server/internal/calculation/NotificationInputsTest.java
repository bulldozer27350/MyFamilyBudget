package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifReachableInput;

class NotificationInputsTest {

    @Test
    @DisplayName("DebitThresholdInput remplace la liste de transactions nulle par une liste vide")
    void testDebitThresholdDefaults() {
        DebitThresholdInput input = new DebitThresholdInput(null, null);

        assertThat(input.threshold()).isNull();
        assertThat(input.recentTransactions()).isEmpty();
    }

    @Test
    @DisplayName("BalanceFloorInput remplace solde de départ et listes nuls par des valeurs neutres")
    void testBalanceFloorDefaults() {
        BalanceFloorInput input = new BalanceFloorInput(null, null, null, null);

        assertThat(input.floor()).isNull();
        assertThat(input.openingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(input.importedTransactions()).isEmpty();
        assertThat(input.pendingOperations()).isEmpty();
    }

    @Test
    @DisplayName("ObjectifReachableInput normalise objectifs, allocations et soldes nuls")
    void testObjectifReachableDefaults() {
        ObjectifReachableInput empty = new ObjectifReachableInput(null, null);
        assertThat(empty.goals()).isEmpty();
        assertThat(empty.placementBalances()).isEmpty();

        ObjectifReachableInput.GoalCoverage goal = new ObjectifReachableInput.GoalCoverage("g1", null, null, null);
        assertThat(goal.label()).isEmpty();
        assertThat(goal.targetAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(goal.allocations()).isEmpty();

        ObjectifReachableInput.Allocation allocation = new ObjectifReachableInput.Allocation("p1", null);
        assertThat(allocation.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Les trois entrées de notification ne référencent aucun modèle budgétaire")
    void testNoBudgetModelDependency() {
        for (Class<?> type : new Class<?>[] { DebitThresholdInput.class, BalanceFloorInput.class,
                ObjectifReachableInput.class }) {
            for (var component : type.getRecordComponents()) {
                assertThat(component.getGenericType().getTypeName())
                        .doesNotContain(".internal.model.");
            }
        }
    }
}
