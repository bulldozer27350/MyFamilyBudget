package com.moe.myfamilybudget.server.internal.notification;

import java.math.BigDecimal;

import com.moe.myfamilybudget.server.internal.notification.rules.BalanceFloorRule;
import com.moe.myfamilybudget.server.internal.notification.rules.DebitThresholdRule;
import com.moe.myfamilybudget.server.internal.notification.rules.ObjectifReachableRule;

/**
 * Paramètres de notification modifiables depuis l'onglet "Notifications" des paramètres. Sans
 * enregistrement, {@link #defaults()} s'applique (toutes les sections désactivées).
 *
 * @param debitThresholdEnabled    section "débit au-delà d'un seuil" activée
 * @param debitThresholdAmount     seuil de débit (euros, valeur absolue)
 * @param balanceFloorEnabled      section "solde sous un seuil" activée
 * @param balanceFloorAmount       seuil plancher (euros)
 * @param objectifReachableEnabled section "objectif atteignable" activée
 */
public record NotificationSettingsParameters(
        Boolean debitThresholdEnabled,
        BigDecimal debitThresholdAmount,
        Boolean balanceFloorEnabled,
        BigDecimal balanceFloorAmount,
        Boolean objectifReachableEnabled) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    public static NotificationSettingsParameters defaults() {
        return new NotificationSettingsParameters(
                false, BigDecimal.valueOf(500), false, BigDecimal.ZERO, false);
    }

    /** Section active pour la clé de règle donnée (voir {@link NotificationRule#key()}). */
    public boolean isEnabled(String ruleKey) {
        return switch (ruleKey) {
            case DebitThresholdRule.KEY -> Boolean.TRUE.equals(debitThresholdEnabled);
            case BalanceFloorRule.KEY -> Boolean.TRUE.equals(balanceFloorEnabled);
            case ObjectifReachableRule.KEY -> Boolean.TRUE.equals(objectifReachableEnabled);
            default -> false;
        };
    }

    /**
     * @throws IllegalArgumentException si un seuil est négatif ou invraisemblable (traduit en 400)
     */
    public void validate() {
        requireInRange(debitThresholdAmount, "debitThresholdAmount");
        requireInRange(balanceFloorAmount, "balanceFloorAmount");
    }

    private static void requireInRange(BigDecimal value, String field) {
        BigDecimal v = value != null ? value : BigDecimal.ZERO;
        if (v.signum() < 0 || v.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Le champ " + field + " doit être compris entre 0 et 10 000 000 €.");
        }
    }
}
