package com.moe.myfamilybudget.domain.notifications.model;

/**
 * Clés stables des règles de notification (SILO-158). Elles sont portées par l'API du silo, et non par les
 * implémentations des règles (qui vivent dans {@code notifications-core}), car elles servent à l'activation
 * individuelle ({@code NotificationSettingsParameters#isEnabled}) et à la déduplication
 * ({@link NotificationMessage#dedupKey()}), c'est-à-dire à des consommateurs qui ne doivent pas connaître le cœur.
 *
 * <p>Chaque règle expose la même valeur par {@link NotificationRule#key()}.
 */
public final class NotificationRuleKeys {

    /** Règle « débit au-delà d'un seuil ». */
    public static final String DEBIT_THRESHOLD = "debit-threshold";

    /** Règle « solde du compte courant sous un seuil ». */
    public static final String BALANCE_FLOOR = "balance-floor";

    /** Règle « objectif atteignable ». */
    public static final String OBJECTIF_REACHABLE = "objectif-reachable";

    private NotificationRuleKeys() {
    }
}
