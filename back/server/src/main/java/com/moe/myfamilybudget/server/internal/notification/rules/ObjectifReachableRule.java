package com.moe.myfamilybudget.server.internal.notification.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.notification.NotificationContext;
import com.moe.myfamilybudget.server.internal.notification.NotificationMessage;
import com.moe.myfamilybudget.server.internal.notification.NotificationRule;

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
@Component
public class ObjectifReachableRule implements NotificationRule {

    public static final String KEY = "objectif-reachable";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<NotificationMessage> check(NotificationContext context) {
        List<ObjectifModel> objectifs = context.data().getEffectiveObjectifs();
        if (objectifs.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> balanceByPlacementId = context.data().getEffectivePlacements().stream()
                .collect(Collectors.toMap(PlacementModel::id,
                        p -> p.balance() != null ? p.balance() : BigDecimal.ZERO,
                        (a, b) -> a));

        List<NotificationMessage> messages = new ArrayList<>();
        for (ObjectifModel objectif : objectifs) {
            BigDecimal target = objectif.getEffectiveTargetAmount();
            if (target.signum() <= 0) {
                continue;
            }
            BigDecimal covered = coveredAmount(objectif.getEffectiveAllocations(), balanceByPlacementId);
            if (covered.compareTo(target) >= 0) {
                messages.add(new NotificationMessage(KEY, objectif.id(), "Objectif atteignable",
                        String.format("\"%s\" est désormais couvert (%s € visés)",
                                objectif.label(), target.toPlainString())));
            }
        }
        return messages;
    }

    private static BigDecimal coveredAmount(List<ObjectifAllocationModel> allocations,
            Map<String, BigDecimal> balanceByPlacementId) {
        BigDecimal covered = BigDecimal.ZERO;
        for (ObjectifAllocationModel allocation : allocations) {
            BigDecimal accountBalance = balanceByPlacementId.getOrDefault(allocation.placementId(), BigDecimal.ZERO);
            covered = covered.add(allocation.getEffectiveAmount().min(accountBalance));
        }
        return covered;
    }
}
