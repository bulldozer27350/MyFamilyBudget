package com.moe.myfamilybudget.application.usecase.notifications;

/**
 * Clé publique VAPID pour les notifications push web/mobiles (R-87, SILO-318).
 *
 * @param publicKey clé publique VAPID, ou null si non configurée
 */
public record PushPublicKeyModel(String publicKey) {
}
