package com.moe.myfamilybudget.server.internal.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.persistence.BudgetMutatedEvent;

/** SILO-180 : une mutation du budget déclenche le contrôle automatique des notifications. */
class NotificationBudgetMutationListenerTest {

    @Test
    @DisplayName("Mutation du budget : le contrôle automatique est lancé")
    void budgetMutationTriggersAutomaticCheck() {
        NotificationCheckService checkService = mock(NotificationCheckService.class);

        new NotificationBudgetMutationListener(checkService).onBudgetMutated(new BudgetMutatedEvent("test"));

        verify(checkService).runAutomaticCheck();
    }
}
