package com.moe.myfamilybudget.application.usecase.notifications;

/**
 * Résultat d'une vérification manuelle des alertes de notification (R-87, SILO-318).
 *
 * @param alertsSent nombre d'alertes envoyées lors du contrôle
 */
public record NotificationVerificationResultModel(int alertsSent) {
}
