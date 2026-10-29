package com.moe.myfamilybudget.application.usecase.notifications;

/**
 * Paramètres de notification lisibles/modifiables (valeurs actuelles + valeurs par défaut) exposés
 * par l'application (R-87, SILO-318).
 *
 * <p>Miroir applicatif du résultat de {@code getNotificationsParametres} et
 * {@code updateNotificationsParametres}.
 *
 * @param values   paramètres actuellement enregistrés
 * @param defaults paramètres par défaut
 */
public record NotificationParametersModel(
        NotificationParametersValuesModel values,
        NotificationParametersValuesModel defaults) {
}
