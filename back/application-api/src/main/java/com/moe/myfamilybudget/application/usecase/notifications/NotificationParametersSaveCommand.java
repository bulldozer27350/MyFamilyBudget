package com.moe.myfamilybudget.application.usecase.notifications;

import java.math.BigDecimal;

/**
 * Commande de mise à jour des paramètres de notification (R-87, SILO-318).
 *
 * <p>Transmise par le module {@code web} (aujourd'hui {@code NotificationsServiceImpl}) après
 * désérialisation du DTO HTTP.
 */
public record NotificationParametersSaveCommand(
        Boolean debitThresholdEnabled,
        BigDecimal debitThresholdAmount,
        Boolean balanceFloorEnabled,
        BigDecimal balanceFloorAmount,
        Boolean objectifReachableEnabled,
        Boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd) {
}
