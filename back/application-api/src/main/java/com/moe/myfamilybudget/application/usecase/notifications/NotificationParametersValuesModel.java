package com.moe.myfamilybudget.application.usecase.notifications;

import java.math.BigDecimal;

/**
 * Valeurs d'un jeu de paramètres de notification (R-87, SILO-318).
 *
 * <p>Miroir applicatif de {@code domain.notifications.rules.NotificationSettingsParameters}.
 *
 * @param debitThresholdEnabled    vrai si l'alerte sur dépense ponctuelle élevée est active
 * @param debitThresholdAmount     seuil en euros au-delà duquel alerter
 * @param balanceFloorEnabled      vrai si l'alerte sur solde bas est active
 * @param balanceFloorAmount       seuil en euros sous lequel alerter
 * @param objectifReachableEnabled vrai si l'alerte sur objectif atteignable est active
 * @param quietHoursEnabled        vrai si la plage silencieuse est active
 * @param quietHoursStart          heure de début (format HH:mm)
 * @param quietHoursEnd            heure de fin (format HH:mm)
 */
public record NotificationParametersValuesModel(
        Boolean debitThresholdEnabled,
        BigDecimal debitThresholdAmount,
        Boolean balanceFloorEnabled,
        BigDecimal balanceFloorAmount,
        Boolean objectifReachableEnabled,
        Boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd) {
}
