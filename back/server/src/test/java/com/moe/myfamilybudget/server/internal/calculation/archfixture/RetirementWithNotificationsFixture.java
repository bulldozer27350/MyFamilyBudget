package com.moe.myfamilybudget.server.internal.calculation.archfixture;

import com.moe.myfamilybudget.domain.notifications.calculation.NotificationDispatchService;

/** Fixture ARCH-020 fautive : un domaine qui dépend de Notifications, consommateur final. */
public abstract class RetirementWithNotificationsFixture {
    protected NotificationDispatchService dependency;
}
