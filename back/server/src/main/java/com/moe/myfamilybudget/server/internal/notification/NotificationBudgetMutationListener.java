package com.moe.myfamilybudget.server.internal.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.persistence.BudgetMutatedEvent;

/**
 * Déclenchement automatique du contrôle des notifications : à chaque {@link BudgetMutatedEvent} (publié par
 * {@code PersistenceManager} après chaque mutation, « toute modification doit déclencher un contrôle des
 * notifications »), après le commit de la transaction en cours ({@link TransactionPhase#AFTER_COMMIT}) pour ne
 * lire que des données effectivement persistées. Reste dans le composition root : l'événement vient de la
 * persistance, que le silo Notifications ne connaît pas (SILO-180).
 */
@Component
public class NotificationBudgetMutationListener {

    private final NotificationCheckService checkService;

    public NotificationBudgetMutationListener(NotificationCheckService checkService) {
        this.checkService = checkService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetMutated(BudgetMutatedEvent event) {
        checkService.runAutomaticCheck();
    }
}
