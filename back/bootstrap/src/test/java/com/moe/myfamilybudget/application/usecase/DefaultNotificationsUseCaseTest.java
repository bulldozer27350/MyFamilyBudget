package com.moe.myfamilybudget.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationVerificationResultModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationsUseCase;
import com.moe.myfamilybudget.application.usecase.notifications.PushPublicKeyModel;
import com.moe.myfamilybudget.application.usecase.notifications.PushSubscriptionCommand;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.model.PushSubscriptionModel;
import com.moe.myfamilybudget.domain.notifications.port.PushSubscriptionStore;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Tests unitaires de {@link DefaultNotificationsUseCase} (R-87, SILO-318).
 */
class DefaultNotificationsUseCaseTest {

    private NotificationSettingsService settingsService;
    private NotificationCheckService checkService;
    private PushSubscriptionStore subscriptionStore;
    private NotificationsUseCase useCase;

    private static final String VAPID_KEY = "BKag8-test-public-key";

    @BeforeEach
    void setUp() {
        settingsService = mock(NotificationSettingsService.class);
        checkService = mock(NotificationCheckService.class);
        subscriptionStore = mock(PushSubscriptionStore.class);

        when(settingsService.defaults()).thenReturn(NotificationSettingsParameters.defaults());
        when(settingsService.current()).thenReturn(NotificationSettingsParameters.defaults());

        useCase = new DefaultNotificationsUseCase(settingsService, checkService, subscriptionStore, VAPID_KEY);
    }

    @Test
    @DisplayName("getNotificationsParametres retourne les paramètres courants et par défaut")
    void getNotificationsParametres() {
        NotificationParametersModel model = useCase.getNotificationsParametres();

        assertNotNull(model.values());
        assertNotNull(model.defaults());
        assertEquals("22:00", model.defaults().quietHoursStart());
        assertEquals("07:00", model.defaults().quietHoursEnd());
    }

    @Test
    @DisplayName("updateNotificationsParametres valide et enregistre les nouvelles valeurs")
    void updateNotificationsParametres() {
        NotificationParametersSaveCommand command = new NotificationParametersSaveCommand(
                true, new BigDecimal("300"), true, new BigDecimal("700"),
                true, true, "23:00", "06:00");

        NotificationSettingsParameters saved = new NotificationSettingsParameters(
                true, new BigDecimal("300"), true, new BigDecimal("700"),
                true, true, "23:00", "06:00");
        when(settingsService.save(any())).thenReturn(saved);

        NotificationParametersModel result = useCase.updateNotificationsParametres(command);

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.values().debitThresholdEnabled());
        assertEquals(0, new BigDecimal("300").compareTo(result.values().debitThresholdAmount()));
        assertEquals("23:00", result.values().quietHoursStart());
    }

    @Test
    @DisplayName("verifyNotifications déclenche le contrôle manuel et retourne le nombre d'alertes")
    void verifyNotifications() {
        when(checkService.runManualCheck()).thenReturn(2);

        NotificationVerificationResultModel result = useCase.verifyNotifications();

        assertEquals(2, result.alertsSent());
        verify(checkService).runManualCheck();
    }

    @Test
    @DisplayName("getPushPublicKey retourne la clé configurée ou null si vide")
    void getPushPublicKey() {
        PushPublicKeyModel model = useCase.getPushPublicKey();
        assertEquals(VAPID_KEY, model.publicKey());

        NotificationsUseCase noKeyUseCase = new DefaultNotificationsUseCase(
                settingsService, checkService, subscriptionStore, "");
        assertNull(noKeyUseCase.getPushPublicKey().publicKey());
    }

    @Test
    @DisplayName("registerPushSubscription enregistre l'abonnement si inexistant")
    void registerPushSubscription_newEndpoint() {
        PushSubscriptionCommand cmd = new PushSubscriptionCommand("https://push.example.com/123", "key123", "auth456");
        when(subscriptionStore.existsByEndpoint("https://push.example.com/123")).thenReturn(false);

        useCase.registerPushSubscription(cmd);

        verify(subscriptionStore).save(any(PushSubscriptionModel.class));
    }

    @Test
    @DisplayName("registerPushSubscription ignore un endpoint déjà existant")
    void registerPushSubscription_existingEndpoint() {
        PushSubscriptionCommand cmd = new PushSubscriptionCommand("https://push.example.com/123", "key123", "auth456");
        when(subscriptionStore.existsByEndpoint("https://push.example.com/123")).thenReturn(true);

        useCase.registerPushSubscription(cmd);

        verify(subscriptionStore, never()).save(any(PushSubscriptionModel.class));
    }

    @Test
    @DisplayName("registerPushSubscription refuse une commande incomplète")
    void registerPushSubscription_invalid() {
        assertThrows(IllegalArgumentException.class, () -> useCase.registerPushSubscription(null));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.registerPushSubscription(new PushSubscriptionCommand(null, "k", "a")));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.registerPushSubscription(new PushSubscriptionCommand("ep", null, "a")));
    }

    @Test
    @DisplayName("unregisterPushSubscription délègue au store")
    void unregisterPushSubscription() {
        useCase.unregisterPushSubscription("https://push.example.com/123");
        verify(subscriptionStore).deleteByEndpoint("https://push.example.com/123");
    }
}
