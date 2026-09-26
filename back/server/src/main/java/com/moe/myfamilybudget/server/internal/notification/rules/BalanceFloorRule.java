package com.moe.myfamilybudget.server.internal.notification.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.notification.NotificationContext;
import com.moe.myfamilybudget.server.internal.notification.NotificationMessage;
import com.moe.myfamilybudget.server.internal.notification.NotificationRule;

/**
 * Signale un solde total (somme des soldes de tous les comptes de patrimoine, {@code
 * PlacementModel.balance}) passé sous le seuil configuré.
 *
 * Règle globale sans sous-entité : un seul message possible par contrôle, clé de déduplication
 * = "balance-floor" (voir {@link NotificationMessage#dedupKey()}).
 */
@Component
public class BalanceFloorRule implements NotificationRule {

    public static final String KEY = "balance-floor";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<NotificationMessage> check(NotificationContext context) {
        BigDecimal floor = context.settings().balanceFloorAmount();
        if (floor == null) {
            return List.of();
        }
        BigDecimal total = context.data().getEffectivePlacements().stream()
                .map(PlacementModel::balance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(floor) >= 0) {
            return List.of();
        }
        return List.of(new NotificationMessage(KEY, null, "Solde sous le seuil",
                String.format("Solde total des comptes : %s € (seuil configuré : %s €)",
                        total.toPlainString(), floor.toPlainString())));
    }
}
