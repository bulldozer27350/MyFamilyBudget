package com.moe.myfamilybudget.domain.notifications.core;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.notifications.calculation.NotificationDispatchService;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.model.NotificationRule;
import com.moe.myfamilybudget.domain.notifications.port.NotificationChannel;
import com.moe.myfamilybudget.domain.notifications.port.NotificationSentLogStore;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Croise les {@link NotificationRule} actives avec la liste des {@link NotificationChannel}, et gère la
 * déduplication.
 *
 * <p>Depuis SILO-180, ce service ne lit plus le budget : l'application lui fournit l'entrée de chaque règle sous
 * forme de fonction des paramètres de notification ({@link RuleInputs}), appelée uniquement pour les règles
 * actives. Le déclenchement automatique après chaque mutation du budget (écoute de l'événement de persistance)
 * reste côté application et composition root.
 *
 * <p>Déclenchement automatique : une alerte n'est envoyée qu'une fois par 24 h (par clé de déduplication), et pas
 * du tout pendant la plage horaire silencieuse si elle est activée
 * ({@link NotificationSettingsParameters#isWithinQuietHours}) : le contrôle est alors purement et simplement
 * sauté (aucune règle évaluée, aucune date de dernier envoi mise à jour), il reprendra normalement à la prochaine
 * mutation une fois la plage terminée.
 *
 * <p>Déclenchement manuel : envoie systématiquement, sans tenir compte du délai de 24 h ni de la plage
 * silencieuse, et remet à jour la date du dernier envoi, ce qui relance aussi le délai de 24 h pour le prochain
 * déclenchement automatique.
 */
public class DefaultNotificationDispatchService implements NotificationDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNotificationDispatchService.class);
    private static final Duration AUTO_TRIGGER_COOLDOWN = Duration.ofHours(24);

    private final DebitThresholdRule debitThresholdRule;
    private final BalanceFloorRule balanceFloorRule;
    private final ObjectifReachableRule objectifReachableRule;
    private final List<NotificationChannel> channels;
    private final NotificationSettingsService settingsService;
    private final NotificationSentLogStore sentLogStore;

    public DefaultNotificationDispatchService(DebitThresholdRule debitThresholdRule,
            BalanceFloorRule balanceFloorRule, ObjectifReachableRule objectifReachableRule,
            List<NotificationChannel> channels, NotificationSettingsService settingsService,
            NotificationSentLogStore sentLogStore) {
        this.debitThresholdRule = debitThresholdRule;
        this.balanceFloorRule = balanceFloorRule;
        this.objectifReachableRule = objectifReachableRule;
        this.channels = channels;
        this.settingsService = settingsService;
        this.sentLogStore = sentLogStore;
    }

    @Override
    public int runAutomaticCheck(RuleInputs inputs) {
        return runChecks(inputs, false);
    }

    @Override
    public int runManualCheck(RuleInputs inputs) {
        return runChecks(inputs, true);
    }

    private int runChecks(RuleInputs inputs, boolean manual) {
        NotificationSettingsParameters settings = settingsService.current();
        if (!manual && settings.isWithinQuietHours(LocalTime.now())) {
            return 0;
        }
        int sent = 0;
        sent += runRule(debitThresholdRule, inputs.debitThreshold(), settings, manual);
        sent += runRule(balanceFloorRule, inputs.balanceFloor(), settings, manual);
        sent += runRule(objectifReachableRule, inputs.objectifReachable(), settings, manual);
        return sent;
    }

    /**
     * Évalue une règle sur son entrée propre (assemblée seulement si la règle est active) et transmet les
     * messages retenus. Une règle, ou un assemblage, en erreur est journalisé et ignoré sans bloquer les autres
     * règles.
     */
    private <I> int runRule(NotificationRule<I> rule,
            Function<NotificationSettingsParameters, I> inputAssembler,
            NotificationSettingsParameters settings, boolean manual) {
        if (!settings.isEnabled(rule.key())) {
            return 0;
        }
        List<NotificationMessage> messages;
        try {
            messages = rule.check(inputAssembler.apply(settings));
        } catch (RuntimeException e) {
            LOG.warn("Règle de notification {} en erreur, ignorée pour ce contrôle", rule.key(), e);
            return 0;
        }
        int sent = 0;
        for (NotificationMessage message : messages) {
            if (!manual && !dueForAutoTrigger(message.dedupKey())) {
                continue;
            }
            dispatchToChannels(message);
            sentLogStore.markSent(message.dedupKey(), Instant.now());
            sent++;
        }
        return sent;
    }

    private void dispatchToChannels(NotificationMessage message) {
        for (NotificationChannel channel : channels) {
            try {
                channel.send(message);
            } catch (RuntimeException e) {
                LOG.warn("Envoi de la notification {} via le canal {} en échec",
                        message.dedupKey(), channel.key(), e);
            }
        }
    }

    private boolean dueForAutoTrigger(String dedupKey) {
        return sentLogStore.findLastSentAt(dedupKey)
                .map(lastSentAt -> Duration.between(lastSentAt, Instant.now()).compareTo(AUTO_TRIGGER_COOLDOWN) >= 0)
                .orElse(true);
    }
}
