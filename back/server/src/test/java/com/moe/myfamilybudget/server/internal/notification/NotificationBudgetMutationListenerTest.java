package com.moe.myfamilybudget.server.internal.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.domain.goals.model.GoalsMutatedEvent;
import com.moe.myfamilybudget.domain.settings.model.AppSettingsMutatedEvent;
import com.moe.myfamilybudget.persistence.BudgetMutatedEvent;

/** SILO-180 : une mutation du budget déclenche le contrôle automatique des notifications (SILO-212 : objectifs inclus). */
class NotificationBudgetMutationListenerTest {

    @Test
    @DisplayName("Mutation du budget : le contrôle automatique est lancé")
    void budgetMutationTriggersAutomaticCheck() {
        NotificationCheckService checkService = mock(NotificationCheckService.class);

        new NotificationBudgetMutationListener(checkService).onBudgetMutated(new BudgetMutatedEvent("test"));

        verify(checkService).runAutomaticCheck();
    }

    @Test
    @DisplayName("Mutation des objectifs : le contrôle automatique est lancé")
    void goalsMutationTriggersAutomaticCheck() {
        NotificationCheckService checkService = mock(NotificationCheckService.class);

        new NotificationBudgetMutationListener(checkService).onGoalsMutated(new GoalsMutatedEvent("saveGoalRow"));

        verify(checkService).runAutomaticCheck();
    }

    @Test
    @DisplayName("Mutation de l'import bancaire : le contrôle automatique est lancé")
    void bankImportMutationTriggersAutomaticCheck() {
        NotificationCheckService checkService = mock(NotificationCheckService.class);

        new NotificationBudgetMutationListener(checkService)
                .onBankImportMutated(new BankImportMutatedEvent("updateBankImport"));

        verify(checkService).runAutomaticCheck();
    }

    @Test
    @DisplayName("Mutation de la simulation ou des hypothèses économiques : le contrôle automatique est lancé")
    void appSettingsMutationTriggersAutomaticCheck() {
        NotificationCheckService checkService = mock(NotificationCheckService.class);

        new NotificationBudgetMutationListener(checkService)
                .onAppSettingsMutated(new AppSettingsMutatedEvent("updateSimulateUntilAge"));

        verify(checkService).runAutomaticCheck();
    }

    @Test
    @DisplayName("Mutation de la trésorerie : le contrôle automatique est lancé")
    void treasuryMutationTriggersAutomaticCheck() {
        NotificationCheckService checkService = mock(NotificationCheckService.class);

        new NotificationBudgetMutationListener(checkService)
                .onTreasuryMutated(new com.moe.myfamilybudget.domain.treasury.model.TreasuryMutatedEvent("addTresorerieRow"));

        verify(checkService).runAutomaticCheck();
    }
}
