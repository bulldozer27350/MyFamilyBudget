package com.moe.myfamilybudget.domain.notifications.calculation;

import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;
import java.util.function.Function;

/**
 * Évalue les règles de notification actives, transmet les messages retenus aux canaux et gère la déduplication
 * (une alerte automatique au plus par 24 h et par clé) ainsi que la plage horaire silencieuse.
 *
 * <p>Le silo ne connaît pas les autres silos : l'application assemble l'entrée de chaque règle à partir de
 * fragments lus ailleurs et la fournit sous forme de fonction des paramètres de notification en vigueur
 * ({@link RuleInputs}). Une fonction n'est appelée que si la règle correspondante est active, et une erreur
 * dans l'une d'elles est journalisée sans bloquer les autres règles. L'implémentation vit dans
 * {@code notifications-core} ({@code DefaultNotificationDispatchService}).
 */
public interface NotificationDispatchService {

    /** Entrées des trois règles, assemblées paresseusement à partir des paramètres de notification. */
    record RuleInputs(
            Function<NotificationSettingsParameters, DebitThresholdInput> debitThreshold,
            Function<NotificationSettingsParameters, BalanceFloorInput> balanceFloor,
            Function<NotificationSettingsParameters, ObjectifReachableInput> objectifReachable) {
    }

    /**
     * Contrôle déclenché par une mutation du budget : sauté pendant la plage silencieuse, une alerte n'est
     * envoyée qu'une fois par 24 h et par clé de déduplication. Retourne le nombre d'alertes envoyées.
     */
    int runAutomaticCheck(RuleInputs inputs);

    /**
     * Contrôle manuel (bouton « Vérifier ») : envoie systématiquement, sans tenir compte du délai de 24 h ni de
     * la plage silencieuse, et relance le délai de 24 h. Retourne le nombre d'alertes envoyées.
     */
    int runManualCheck(RuleInputs inputs);
}
