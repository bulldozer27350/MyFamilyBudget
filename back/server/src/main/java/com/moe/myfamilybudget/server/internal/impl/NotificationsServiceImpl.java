package com.moe.myfamilybudget.server.internal.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.NotificationsApi;
import com.moe.myfamilybudget.api.model.NotificationParametresDto;
import com.moe.myfamilybudget.api.model.NotificationParametresValuesDto;
import com.moe.myfamilybudget.api.model.NotificationVerificationResultDto;
import com.moe.myfamilybudget.api.model.PushPublicKeyDto;
import com.moe.myfamilybudget.api.model.PushSubscriptionDto;
import com.moe.myfamilybudget.server.internal.mapper.NotificationsMapper;
import com.moe.myfamilybudget.application.notification.NotificationCheckService;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.model.PushSubscriptionModel;
import com.moe.myfamilybudget.domain.notifications.port.PushSubscriptionStore;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * Contrôleur REST implémentant le contrat OpenAPI NotificationsApi (tag Notifications) : façade
 * mince, sur le modèle d'{@code AnalysePretsServiceImpl}.
 */
@RestController
public class NotificationsServiceImpl implements NotificationsApi {

    private final NotificationSettingsService settingsService;
    private final NotificationCheckService checkService;
    private final PushSubscriptionStore subscriptionStore;
    private final NotificationsMapper mapper;

    @Value("${myfamilybudget.notifications.push.vapid-public-key:}")
    private String vapidPublicKey;

    public NotificationsServiceImpl(NotificationSettingsService settingsService,
            NotificationCheckService checkService, PushSubscriptionStore subscriptionStore,
            NotificationsMapper mapper) {
        this.settingsService = settingsService;
        this.checkService = checkService;
        this.subscriptionStore = subscriptionStore;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<NotificationParametresDto> getNotificationsParametres() {
        return ResponseEntity.ok(mapper.toParametresDto(settingsService.current(), settingsService.defaults()));
    }

    @Override
    public ResponseEntity<NotificationParametresDto> updateNotificationsParametres(
            NotificationParametresValuesDto body) {
        NotificationSettingsParameters saved = settingsService.save(mapper.toParameters(body));
        return ResponseEntity.ok(mapper.toParametresDto(saved, settingsService.defaults()));
    }

    @Override
    public ResponseEntity<NotificationVerificationResultDto> verifyNotifications() {
        int sent = checkService.runManualCheck();
        NotificationVerificationResultDto dto = new NotificationVerificationResultDto();
        dto.setAlertsSent(sent);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<PushPublicKeyDto> getPushPublicKey() {
        PushPublicKeyDto dto = new PushPublicKeyDto();
        dto.setPublicKey(vapidPublicKey == null || vapidPublicKey.isBlank() ? null : vapidPublicKey);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Void> registerPushSubscription(PushSubscriptionDto body) {
        if (body == null || body.getEndpoint() == null || body.getEndpoint().isBlank()
                || body.getKeys() == null || body.getKeys().getP256dh() == null
                || body.getKeys().getAuth() == null) {
            throw new IllegalArgumentException("Abonnement push incomplet (endpoint et clés requis).");
        }
        if (!subscriptionStore.existsByEndpoint(body.getEndpoint())) {
            // Déjà enregistré sinon : endpoint stable, rien à faire.
            subscriptionStore.save(new PushSubscriptionModel(
                    UUID.randomUUID().toString(), body.getEndpoint(),
                    body.getKeys().getP256dh(), body.getKeys().getAuth(), Instant.now()));
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unregisterPushSubscription(String endpoint) {
        subscriptionStore.deleteByEndpoint(endpoint);
        return ResponseEntity.noContent().build();
    }
}
