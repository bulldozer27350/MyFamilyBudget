package com.moe.myfamilybudget.application.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersValuesModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationVerificationResultModel;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationsUseCase;
import com.moe.myfamilybudget.application.usecase.notifications.PushPublicKeyModel;
import com.moe.myfamilybudget.application.usecase.notifications.PushSubscriptionCommand;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.model.PushSubscriptionModel;
import com.moe.myfamilybudget.domain.notifications.port.PushSubscriptionStore;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Implémentation du cas d'usage « Notifications » (R-87, SILO-318).
 *
 * <p>Ce cas d'usage orchestre les paramètres de notification ({@link NotificationSettingsService}),
 * le contrôle manuel des alertes ({@link NotificationCheckService}), et la gestion des abonnements
 * push ({@link PushSubscriptionStore}), sans {@code ResponseEntity} ni DTO.
 *
 * <p>Le module {@code web} (aujourd'hui {@code NotificationsServiceImpl}) convertit les résultats
 * vers le monde HTTP.
 */
@Service
public class DefaultNotificationsUseCase implements NotificationsUseCase {

    private final NotificationSettingsService settingsService;
    private final NotificationCheckService checkService;
    private final PushSubscriptionStore subscriptionStore;
    private final String vapidPublicKey;

    public DefaultNotificationsUseCase(
            NotificationSettingsService settingsService,
            NotificationCheckService checkService,
            PushSubscriptionStore subscriptionStore,
            @Value("${myfamilybudget.notifications.push.vapid-public-key:}") String vapidPublicKey) {
        this.settingsService = settingsService;
        this.checkService = checkService;
        this.subscriptionStore = subscriptionStore;
        this.vapidPublicKey = vapidPublicKey;
    }

    @Override
    public NotificationParametersModel getNotificationsParametres() {
        return toParametresModel(settingsService.current(), settingsService.defaults());
    }

    @Override
    public NotificationParametersModel updateNotificationsParametres(NotificationParametersSaveCommand command) {
        NotificationSettingsParameters domainParams = toDomainParameters(command);
        NotificationSettingsParameters saved = settingsService.save(domainParams);
        return toParametresModel(saved, settingsService.defaults());
    }

    @Override
    public NotificationVerificationResultModel verifyNotifications() {
        int sent = checkService.runManualCheck();
        return new NotificationVerificationResultModel(sent);
    }

    @Override
    public PushPublicKeyModel getPushPublicKey() {
        String key = (vapidPublicKey == null || vapidPublicKey.isBlank()) ? null : vapidPublicKey;
        return new PushPublicKeyModel(key);
    }

    @Override
    public void registerPushSubscription(PushSubscriptionCommand command) {
        if (command == null || command.endpoint() == null || command.endpoint().isBlank()
                || command.p256dh() == null || command.auth() == null) {
            throw new IllegalArgumentException("Abonnement push incomplet (endpoint et clés requis).");
        }
        if (!subscriptionStore.existsByEndpoint(command.endpoint())) {
            subscriptionStore.save(new PushSubscriptionModel(
                    UUID.randomUUID().toString(), command.endpoint(),
                    command.p256dh(), command.auth(), Instant.now()));
        }
    }

    @Override
    public void unregisterPushSubscription(String endpoint) {
        subscriptionStore.deleteByEndpoint(endpoint);
    }

    // ------------------------------------------------------------------ traduction vers application-api

    private NotificationParametersModel toParametresModel(NotificationSettingsParameters current,
            NotificationSettingsParameters defaults) {
        return new NotificationParametersModel(toValuesModel(current), toValuesModel(defaults));
    }

    private NotificationParametersValuesModel toValuesModel(NotificationSettingsParameters p) {
        return new NotificationParametersValuesModel(
                p.debitThresholdEnabled(),
                p.debitThresholdAmount(),
                p.balanceFloorEnabled(),
                p.balanceFloorAmount(),
                p.objectifReachableEnabled(),
                p.quietHoursEnabled(),
                p.quietHoursStart(),
                p.quietHoursEnd());
    }

    private NotificationSettingsParameters toDomainParameters(NotificationParametersSaveCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Commande de paramètres de notification manquante.");
        }
        NotificationSettingsParameters defaults = settingsService.defaults();
        return new NotificationSettingsParameters(
                Boolean.TRUE.equals(command.debitThresholdEnabled()),
                command.debitThresholdAmount() != null ? command.debitThresholdAmount() : java.math.BigDecimal.ZERO,
                Boolean.TRUE.equals(command.balanceFloorEnabled()),
                command.balanceFloorAmount() != null ? command.balanceFloorAmount() : java.math.BigDecimal.ZERO,
                Boolean.TRUE.equals(command.objectifReachableEnabled()),
                Boolean.TRUE.equals(command.quietHoursEnabled()),
                (command.quietHoursStart() != null && !command.quietHoursStart().isBlank())
                        ? command.quietHoursStart() : defaults.quietHoursStart(),
                (command.quietHoursEnd() != null && !command.quietHoursEnd().isBlank())
                        ? command.quietHoursEnd() : defaults.quietHoursEnd());
    }
}
