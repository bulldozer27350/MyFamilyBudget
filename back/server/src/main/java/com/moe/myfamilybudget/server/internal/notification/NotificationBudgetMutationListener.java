package com.moe.myfamilybudget.server.internal.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.domain.credit.model.LoansMutatedEvent;
import com.moe.myfamilybudget.domain.goals.model.GoalsMutatedEvent;
import com.moe.myfamilybudget.domain.treasury.model.TreasuryMutatedEvent;
import com.moe.myfamilybudget.persistence.BudgetMutatedEvent;

/**
 * Déclenchement automatique du contrôle des notifications : à chaque {@link BudgetMutatedEvent} (publié par
 * {@code PersistenceManager} après chaque mutation, « toute modification doit déclencher un contrôle des
 * notifications »), après le commit de la transaction en cours ({@link TransactionPhase#AFTER_COMMIT}) pour ne
 * lire que des données effectivement persistées. Reste dans le composition root : l'événement vient de la
 * persistance, que le silo Notifications ne connaît pas (SILO-180).
 *
 * <p>SILO-212 (lot B1) : les objectifs s'écrivent directement dans leur silo, sans {@code PersistenceManager} ;
 * leur adaptateur publie un {@link GoalsMutatedEvent}, traité de la même façon (même contrôle, même phase).
 * SILO-213 (lot B) : même principe pour l'import bancaire ({@link BankImportMutatedEvent}).
 * SILO-214 (lot B) : même principe pour les prêts ({@link LoansMutatedEvent}).
 * SILO-216 (lot B1) : même principe pour la trésorerie ({@link TreasuryMutatedEvent}).
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalsMutated(GoalsMutatedEvent event) {
        checkService.runAutomaticCheck();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBankImportMutated(BankImportMutatedEvent event) {
        checkService.runAutomaticCheck();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoansMutated(LoansMutatedEvent event) {
        checkService.runAutomaticCheck();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTreasuryMutated(TreasuryMutatedEvent event) {
        checkService.runAutomaticCheck();
    }
}
