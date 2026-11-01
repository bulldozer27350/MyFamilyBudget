package com.moe.myfamilybudget.domain.notifications.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import com.moe.myfamilybudget.domain.notifications.calculation.ObjectifReachableInput;
import com.moe.myfamilybudget.domain.notifications.calculation.PlacementBalanceSnapshot;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.model.NotificationRule;
import com.moe.myfamilybudget.domain.notifications.model.NotificationRuleKeys;

/**
 * Signale chaque objectif dont le montant visé est désormais couvert par ses allocations
 * multi-comptes.
 *
 * Pour chaque allocation, le montant réellement couvert est plafonné au solde courant du compte
 * ({@code min(allocation.amount, compte.balance)}) : un compte dont le solde a baissé depuis
 * l'allocation ne compte que pour ce qu'il porte réellement. Cette approximation reprend l'esprit
 * de {@code computeTresorerieDisponible}/{@code computeGoalReallocation} côté front
 * (calculations.js) sans les reprendre à l'identique — à vérifier avec Marco si un écart de
 * couverture affichée apparaît entre le front et une notification reçue.
 *
 * Une règle par objectif (clé de déduplication = "objectif-reachable:&lt;idObjectif&gt;") : un
 * objectif nouvellement couvert redevient positif à chaque contrôle tant qu'il reste couvert, la
 * déduplication (24h) évitant le spam.
 */
public class ObjectifReachableRule implements NotificationRule<ObjectifReachableInput> {

    public static final String KEY = NotificationRuleKeys.OBJECTIF_REACHABLE;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<NotificationMessage> check(ObjectifReachableInput input) {
        if (input.goals().isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> balanceByPlacementId = input.placementBalances().stream()
                .collect(Collectors.toMap(PlacementBalanceSnapshot::placementId,
                        PlacementBalanceSnapshot::balance,
                        (a, b) -> a));

        List<NotificationMessage> messages = new ArrayList<>();
        for (ObjectifReachableInput.GoalCoverage goal : input.goals()) {
            BigDecimal target = goal.targetAmount();
            if (target.signum() <= 0) {
                continue;
            }
            BigDecimal covered = coveredAmount(goal.allocations(), balanceByPlacementId);
            if (covered.compareTo(target) >= 0) {
                messages.add(new NotificationMessage(KEY, goal.id(), "Objectif atteignable",
                        String.format("\"%s\" est désormais couvert (%s € visés)",
                                goal.label(), target.toPlainString())));
            }
        }
        return messages;
    }

    private static BigDecimal coveredAmount(List<ObjectifReachableInput.Allocation> allocations,
            Map<String, BigDecimal> balanceByPlacementId) {
        BigDecimal covered = BigDecimal.ZERO;
        for (ObjectifReachableInput.Allocation allocation : allocations) {
            BigDecimal accountBalance = balanceByPlacementId.getOrDefault(allocation.placementId(), BigDecimal.ZERO);
            covered = covered.add(allocation.amount().min(accountBalance));
        }
        return covered;
    }
}
