package com.moe.myfamilybudget.server.internal.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.BudgetMutatedEvent;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.entity.NotificationSentLogEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.NotificationSentLogRepository;

/**
 * Croise la liste des {@link NotificationRule} actives avec la liste des {@link NotificationChannel},
 * et gère la déduplication.
 *
 * Déclenchement automatique : à chaque {@link BudgetMutatedEvent} (publié par
 * {@link PersistenceManager} après chaque mutation — "toute modification doit déclencher un
 * contrôle des notifications"), après le commit de la transaction en cours
 * ({@link TransactionPhase#AFTER_COMMIT}) pour ne lire que des données effectivement persistées.
 * Une alerte automatique n'est envoyée qu'une fois par 24h (par clé de déduplication).
 *
 * Déclenchement manuel : {@link #runManualCheck()} (bouton "Vérifier") envoie systématiquement,
 * sans tenir compte du délai de 24h, et remet à jour la date du dernier envoi — ce qui relance
 * aussi le délai de 24h pour le prochain déclenchement automatique.
 */
@Service
public class NotificationDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final Duration AUTO_TRIGGER_COOLDOWN = Duration.ofHours(24);

    private final List<NotificationRule> rules;
    private final List<NotificationChannel> channels;
    private final NotificationSettingsService settingsService;
    private final PersistenceManager persistenceManager;
    private final NotificationSentLogRepository sentLogRepository;

    public NotificationDispatchService(List<NotificationRule> rules, List<NotificationChannel> channels,
            NotificationSettingsService settingsService, PersistenceManager persistenceManager,
            NotificationSentLogRepository sentLogRepository) {
        this.rules = rules;
        this.channels = channels;
        this.settingsService = settingsService;
        this.persistenceManager = persistenceManager;
        this.sentLogRepository = sentLogRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetMutated(BudgetMutatedEvent event) {
        runChecks(false);
    }

    /** Déclenchement manuel (bouton "Vérifier"). Retourne le nombre d'alertes envoyées. */
    public int runManualCheck() {
        return runChecks(true);
    }

    private int runChecks(boolean manual) {
        NotificationSettingsParameters settings = settingsService.current();
        BudgetDataModel data = persistenceManager.getBudgetData();
        NotificationContext context = new NotificationContext(data, settings);
        int sent = 0;
        for (NotificationRule rule : rules) {
            if (!settings.isEnabled(rule.key())) {
                continue;
            }
            List<NotificationMessage> messages;
            try {
                messages = rule.check(context);
            } catch (RuntimeException e) {
                LOG.warn("Règle de notification {} en erreur, ignorée pour ce contrôle", rule.key(), e);
                continue;
            }
            for (NotificationMessage message : messages) {
                if (!manual && !dueForAutoTrigger(message.dedupKey())) {
                    continue;
                }
                dispatchToChannels(message);
                markSent(message.dedupKey());
                sent++;
            }
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
        return sentLogRepository.findById(dedupKey)
                .map(log -> Duration.between(log.getLastSentAt(), Instant.now()).compareTo(AUTO_TRIGGER_COOLDOWN) >= 0)
                .orElse(true);
    }

    private void markSent(String dedupKey) {
        sentLogRepository.save(new NotificationSentLogEntity(dedupKey, Instant.now()));
    }
}
