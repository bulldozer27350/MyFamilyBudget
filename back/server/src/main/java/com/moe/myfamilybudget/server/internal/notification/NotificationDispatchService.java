package com.moe.myfamilybudget.server.internal.notification;

import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.model.NotificationRule;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.moe.myfamilybudget.application.factory.NotificationInputFactory;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.persistence.BudgetMutatedEvent;
import com.moe.myfamilybudget.persistence.entity.NotificationSentLogEntity;
import com.moe.myfamilybudget.domain.notifications.rules.BalanceFloorRule;
import com.moe.myfamilybudget.domain.notifications.rules.DebitThresholdRule;
import com.moe.myfamilybudget.domain.notifications.rules.ObjectifReachableRule;
import com.moe.myfamilybudget.persistence.repository.NotificationSentLogRepository;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;

/**
 * Croise les {@link NotificationRule} actives avec la liste des {@link NotificationChannel}, et
 * gère la déduplication.
 *
 * Depuis RF-702, ce service assemble en amont l'entrée propre à chaque règle (voir
 * {@link NotificationInputFactory}) avant de l'évaluer : les règles ne connaissent plus le budget.
 * Depuis NOTIF-010, cet assemblage lit uniquement les fragments nécessaires à chaque règle via les
 * ports de lecture existants ({@link BankReader}, {@link SettingsReader}, {@link GoalReader},
 * {@link PatrimoineReader}) : ce service ne dépend plus de {@code PersistenceManager} ni du budget
 * global. Les lectures ne sont effectuées que pour les règles actives.
 *
 * Déclenchement automatique : à chaque {@link BudgetMutatedEvent} (publié par
 * {@code PersistenceManager} après chaque mutation — "toute modification doit déclencher un
 * contrôle des notifications"), après le commit de la transaction en cours
 * ({@link TransactionPhase#AFTER_COMMIT}) pour ne lire que des données effectivement persistées.
 * Une alerte automatique n'est envoyée qu'une fois par 24h (par clé de déduplication), et pas du
 * tout pendant la plage horaire silencieuse si elle est activée
 * ({@link NotificationSettingsParameters#isWithinQuietHours}) : le contrôle est alors purement et
 * simplement sauté (aucune règle évaluée, aucune date de dernier envoi mise à jour), il reprendra
 * normalement à la prochaine mutation une fois la plage terminée.
 *
 * Déclenchement manuel : {@link #runManualCheck()} (bouton "Vérifier") envoie systématiquement,
 * sans tenir compte du délai de 24h ni de la plage silencieuse, et remet à jour la date du dernier
 * envoi — ce qui relance aussi le délai de 24h pour le prochain déclenchement automatique.
 */
@Service
public class NotificationDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final Duration AUTO_TRIGGER_COOLDOWN = Duration.ofHours(24);

    private final DebitThresholdRule debitThresholdRule;
    private final BalanceFloorRule balanceFloorRule;
    private final ObjectifReachableRule objectifReachableRule;
    private final List<NotificationChannel> channels;
    private final NotificationSettingsService settingsService;
    private final BankReader bankReader;
    private final SettingsReader settingsReader;
    private final GoalReader goalReader;
    private final PatrimoineReader patrimoineReader;
    private final NotificationSentLogRepository sentLogRepository;

    public NotificationDispatchService(DebitThresholdRule debitThresholdRule, BalanceFloorRule balanceFloorRule,
            ObjectifReachableRule objectifReachableRule, List<NotificationChannel> channels,
            NotificationSettingsService settingsService, BankReader bankReader, SettingsReader settingsReader,
            GoalReader goalReader, PatrimoineReader patrimoineReader,
            NotificationSentLogRepository sentLogRepository) {
        this.debitThresholdRule = debitThresholdRule;
        this.balanceFloorRule = balanceFloorRule;
        this.objectifReachableRule = objectifReachableRule;
        this.channels = channels;
        this.settingsService = settingsService;
        this.bankReader = bankReader;
        this.settingsReader = settingsReader;
        this.goalReader = goalReader;
        this.patrimoineReader = patrimoineReader;
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
        if (!manual && settings.isWithinQuietHours(LocalTime.now())) {
            return 0;
        }
        int sent = 0;
        sent += runRule(debitThresholdRule,
                () -> NotificationInputFactory.debitThreshold(
                        bankReader.getBankImport(), settings.debitThresholdAmount()),
                settings, manual);
        sent += runRule(balanceFloorRule,
                () -> NotificationInputFactory.balanceFloor(
                        bankReader.getBankImport(), openingBalance(), settings.balanceFloorAmount()),
                settings, manual);
        sent += runRule(objectifReachableRule,
                () -> NotificationInputFactory.objectifReachable(
                        goalReader.getGoals(), patrimoineReader.getPlacements()),
                settings, manual);
        return sent;
    }

    /** Solde de départ des paramètres budgétaires ({@code null} traité comme zéro par la factory). */
    private BigDecimal openingBalance() {
        SettingsModel budgetSettings = settingsReader.getSettings();
        return budgetSettings != null ? budgetSettings.getEffectiveStartBalance() : null;
    }

    /**
     * Évalue une règle sur son entrée propre (assemblée seulement si la règle est active) et
     * transmet les messages retenus. Une règle, ou un assemblage, en erreur est journalisé et
     * ignoré sans bloquer les autres règles.
     */
    private <I> int runRule(NotificationRule<I> rule, Supplier<I> inputSupplier,
            NotificationSettingsParameters settings, boolean manual) {
        if (!settings.isEnabled(rule.key())) {
            return 0;
        }
        List<NotificationMessage> messages;
        try {
            messages = rule.check(inputSupplier.get());
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
            markSent(message.dedupKey());
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
        return sentLogRepository.findById(dedupKey)
                .map(log -> Duration.between(log.getLastSentAt(), Instant.now()).compareTo(AUTO_TRIGGER_COOLDOWN) >= 0)
                .orElse(true);
    }

    private void markSent(String dedupKey) {
        sentLogRepository.save(new NotificationSentLogEntity(dedupKey, Instant.now()));
    }
}
