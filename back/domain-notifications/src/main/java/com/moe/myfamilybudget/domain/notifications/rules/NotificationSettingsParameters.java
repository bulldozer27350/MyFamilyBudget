package com.moe.myfamilybudget.domain.notifications.rules;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import com.moe.myfamilybudget.domain.notifications.rules.BalanceFloorRule;
import com.moe.myfamilybudget.domain.notifications.rules.DebitThresholdRule;
import com.moe.myfamilybudget.domain.notifications.rules.ObjectifReachableRule;

/**
 * Paramètres de notification modifiables depuis l'onglet "Notifications" des paramètres. Sans
 * enregistrement, {@link #defaults()} s'applique (toutes les sections désactivées).
 *
 * @param debitThresholdEnabled    section "débit au-delà d'un seuil" activée
 * @param debitThresholdAmount     seuil de débit (euros, valeur absolue)
 * @param balanceFloorEnabled      section "solde sous un seuil" activée
 * @param balanceFloorAmount       seuil plancher (euros)
 * @param objectifReachableEnabled section "objectif atteignable" activée
 * @param quietHoursEnabled        plage horaire silencieuse activée (aucun envoi automatique
 *                                 pendant cette plage ; un déclenchement manuel via le bouton
 *                                 "Vérifier" l'ignore toujours)
 * @param quietHoursStart          début de la plage silencieuse, format "HH:mm"
 * @param quietHoursEnd            fin de la plage silencieuse, format "HH:mm" ; peut être
 *                                 antérieure à {@code quietHoursStart} (plage chevauchant minuit,
 *                                 ex. 22:00 → 07:00)
 */
public record NotificationSettingsParameters(
        Boolean debitThresholdEnabled,
        BigDecimal debitThresholdAmount,
        Boolean balanceFloorEnabled,
        BigDecimal balanceFloorAmount,
        Boolean objectifReachableEnabled,
        Boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    public static NotificationSettingsParameters defaults() {
        return new NotificationSettingsParameters(
                false, BigDecimal.valueOf(500), false, BigDecimal.ZERO, false,
                false, "22:00", "07:00");
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
     * @return {@code true} si un déclenchement automatique doit être suspendu à l'instant présent
     *         (heure du serveur). Ne s'applique jamais à un déclenchement manuel (bouton
     *         "Vérifier"), qui l'ignore toujours — voir {@code NotificationDispatchService}.
     */
    public boolean isWithinQuietHours(LocalTime now) {
        if (!Boolean.TRUE.equals(quietHoursEnabled)) {
            return false;
        }
        LocalTime start = parseTime(quietHoursStart);
        LocalTime end = parseTime(quietHoursEnd);
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // Plage chevauchant minuit (ex. 22:00 -> 07:00).
        return !now.isBefore(start) || now.isBefore(end);
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @throws IllegalArgumentException si un seuil est négatif ou invraisemblable, ou si une
     *                                   heure de plage silencieuse est fournie dans un format
     *                                   invalide (traduit en 400)
     */
    public void validate() {
        requireInRange(debitThresholdAmount, "debitThresholdAmount");
        requireInRange(balanceFloorAmount, "balanceFloorAmount");
        requireValidTime(quietHoursStart, "quietHoursStart");
        requireValidTime(quietHoursEnd, "quietHoursEnd");
    }

    private static void requireInRange(BigDecimal value, String field) {
        BigDecimal v = value != null ? value : BigDecimal.ZERO;
        if (v.signum() < 0 || v.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Le champ " + field + " doit être compris entre 0 et 10 000 000 €.");
        }
    }

    private static void requireValidTime(String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (parseTime(value) == null) {
            throw new IllegalArgumentException(
                    "Le champ " + field + " doit être au format HH:mm (ex. 22:00).");
        }
    }
}
