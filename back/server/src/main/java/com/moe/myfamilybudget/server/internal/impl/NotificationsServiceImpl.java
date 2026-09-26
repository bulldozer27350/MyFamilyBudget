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
import com.moe.myfamilybudget.server.internal.notification.NotificationDispatchService;
import com.moe.myfamilybudget.server.internal.notification.NotificationSettingsParameters;
import com.moe.myfamilybudget.server.internal.notification.NotificationSettingsService;
import com.moe.myfamilybudget.server.internal.persistence.entity.PushSubscriptionEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.PushSubscriptionRepository;

/**
 * Contrôleur REST implémentant le contrat OpenAPI NotificationsApi (tag Notifications) : façade
 * mince, sur le modèle d'{@code AnalysePretsServiceImpl}.
 */
@RestController
public class NotificationsServiceImpl implements NotificationsApi {

    private final NotificationSettingsService settingsService;
    private final NotificationDispatchService dispatchService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationsMapper mapper;

    @Value("${myfamilybudget.notifications.push.vapid-public-key:}")
    private String vapidPublicKey;

    public NotificationsServiceImpl(NotificationSettingsService settingsService,
            NotificationDispatchService dispatchService, PushSubscriptionRepository subscriptionRepository,
            NotificationsMapper mapper) {
        this.settingsService = settingsService;
        this.dispatchService = dispatchService;
        this.subscriptionRepository = subscriptionRepository;
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
        int sent = dispatchService.runManualCheck();
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
        subscriptionRepository.findByEndpoint(body.getEndpoint()).ifPresentOrElse(
                existing -> { /* déjà enregistré : endpoint stable, rien à faire */ },
                () -> subscriptionRepository.save(new PushSubscriptionEntity(
                        UUID.randomUUID().toString(), body.getEndpoint(),
                        body.getKeys().getP256dh(), body.getKeys().getAuth(), Instant.now())));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unregisterPushSubscription(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
        return ResponseEntity.noContent().build();
    }
}
