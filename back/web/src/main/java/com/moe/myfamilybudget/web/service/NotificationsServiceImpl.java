package com.moe.myfamilybudget.web.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.NotificationsApi;
import com.moe.myfamilybudget.api.model.NotificationParametresDto;
import com.moe.myfamilybudget.api.model.NotificationParametresValuesDto;
import com.moe.myfamilybudget.api.model.NotificationVerificationResultDto;
import com.moe.myfamilybudget.api.model.PushPublicKeyDto;
import com.moe.myfamilybudget.api.model.PushSubscriptionDto;
import com.moe.myfamilybudget.web.mapper.NotificationsMapper;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.notifications.NotificationsUseCase;
import com.moe.myfamilybudget.application.usecase.notifications.PushSubscriptionCommand;

/**
 * Contrôleur REST implémentant le contrat OpenAPI NotificationsApi (tag Notifications) : façade
 * mince, tout le traitement métier est dans {@link NotificationsUseCase} (R-87, SILO-318, même
 * gabarit que {@code AnalysePretsServiceImpl}/{@code OverviewServiceImpl}).
 *
 * <p>Ce contrôleur ne garde que la conversion HTTP ({@link ResponseEntity}, DTOs).
 */
@RestController
public class NotificationsServiceImpl implements NotificationsApi {

    private final NotificationsUseCase notificationsUseCase;
    private final NotificationsMapper mapper;

    public NotificationsServiceImpl(NotificationsUseCase notificationsUseCase, NotificationsMapper mapper) {
        this.notificationsUseCase = notificationsUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<NotificationParametresDto> getNotificationsParametres() {
        return ResponseEntity.ok(mapper.toParametresDto(notificationsUseCase.getNotificationsParametres()));
    }

    @Override
    public ResponseEntity<NotificationParametresDto> updateNotificationsParametres(
            NotificationParametresValuesDto body) {
        NotificationParametersSaveCommand command = mapper.toSaveCommand(body);
        return ResponseEntity.ok(mapper.toParametresDto(notificationsUseCase.updateNotificationsParametres(command)));
    }

    @Override
    public ResponseEntity<NotificationVerificationResultDto> verifyNotifications() {
        return ResponseEntity.ok(mapper.toVerificationResultDto(notificationsUseCase.verifyNotifications()));
    }

    @Override
    public ResponseEntity<PushPublicKeyDto> getPushPublicKey() {
        return ResponseEntity.ok(mapper.toPushPublicKeyDto(notificationsUseCase.getPushPublicKey()));
    }

    @Override
    public ResponseEntity<Void> registerPushSubscription(PushSubscriptionDto body) {
        PushSubscriptionCommand command = mapper.toPushSubscriptionCommand(body);
        notificationsUseCase.registerPushSubscription(command);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unregisterPushSubscription(String endpoint) {
        notificationsUseCase.unregisterPushSubscription(endpoint);
        return ResponseEntity.noContent().build();
    }
}
